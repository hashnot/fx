package com.hashnot.xchange.event.impl;

import com.hashnot.xchange.event.IUserTradesMonitor;
import com.hashnot.xchange.event.IUserTradesListener;
import com.hashnot.xchange.event.UserTradesEvent;
import com.hashnot.xchange.event.impl.exec.RunnableScheduler;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.polling.opt.TradeHistorySinceTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;

/**
 * @author Rafał Krupiński
 */
public class UserTradesMonitor extends AbstractPollingMonitor implements IUserTradesMonitor {
    final private Logger log = LoggerFactory.getLogger(UserTradesMonitor.class);

    private final Exchange exchange;

    private final Set<IUserTradesListener> listeners = new HashSet<>();

    private Date previous;

    @Override
    public void run() {
        PollingTradeService tradeService = exchange.getPollingTradeService();

        Object params = tradeService.createTradeHistoryParams();
        if (params instanceof TradeHistorySinceTime)
            ((TradeHistorySinceTime) params).setFromTime(previous);

        UserTrades trades;
        try {
            log.debug("Getting trades from {}", exchange);
            trades = tradeService.getTradeHistory(params);
            log.debug("User trades {}", trades.getUserTrades().size());
        } catch (IOException e) {
            log.warn("Error from {}", tradeService, e);
            return;
        }

        List<UserTrade> tradeList = trades.getUserTrades();
        if (tradeList.isEmpty()) {
            previous = new Date();
            return;
        } else if (tradeList.size() == 1) {
            UserTrade userTrade = tradeList.get(0);
            previous = userTrade.getTimestamp() != null ? userTrade.getTimestamp() : new Date();
        } else {
            if (Trades.TradeSortType.SortByTimestamp == trades.getTradeSortType()) {
                UserTrade first = tradeList.get(0);
                UserTrade last = tradeList.get(tradeList.size() - 1);
                log.debug("first: {}, last: {}", first.getTimestamp(), last.getTimestamp());
                if (first.getTimestamp() != null)
                    previous = first.getTimestamp().compareTo(last.getTimestamp()) < 0 ? last.getTimestamp() : first.getTimestamp();
                else
                    previous = new Date();
            } else
                log.warn("Sorting by ID @", exchange);
        }

        multiplex(listeners, new UserTradesEvent(trades, exchange), (l, e) -> l.trades(e));
    }

    public UserTradesMonitor(Exchange exchange, RunnableScheduler scheduler) {
        super(scheduler);
        this.exchange = exchange;
    }

    @Override
    public void addTradesListener(IUserTradesListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
            enable();
        }
    }

    @Override
    public void removeTradesListener(IUserTradesListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
            if (listeners.isEmpty())
                disable();
        }
    }
}
