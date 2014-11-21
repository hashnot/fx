package com.hashnot.xchange.event.trade.impl;

import com.hashnot.xchange.async.RunnableScheduler;
import com.hashnot.xchange.event.AbstractPollingMonitor;
import com.hashnot.xchange.event.trade.IUserTradesListener;
import com.hashnot.xchange.event.trade.IUserTradesMonitor;
import com.hashnot.xchange.event.trade.UserTradesEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.polling.trade.DefaultTradeHistoryParamsTimeSpan;
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

        Date now = new Date();

        UserTrades trades;
        try {
            log.debug("Getting trades from {}", exchange);
            trades = tradeService.getTradeHistory(new DefaultTradeHistoryParamsTimeSpan(previous));
            log.debug("User trades {}", trades.getUserTrades().size());
        } catch (IOException e) {
            log.warn("Error from {}", tradeService, e);
            return;
        }

        if (trades.getUserTrades().isEmpty()) {
            return;
        }

        updatePreviousDate(trades, now);

        multiplex(listeners, new UserTradesEvent(trades, exchange), IUserTradesListener::trades);
    }

    protected void updatePreviousDate(UserTrades trades, Date now) {
        List<UserTrade> tradeList = trades.getUserTrades();

        // never empty here
        if (tradeList.size() == 1) {
            UserTrade userTrade = tradeList.get(0);
            previous = userTrade.getTimestamp() != null ? userTrade.getTimestamp() : now;
        } else {
            UserTrade first = tradeList.get(0);
            if (Trades.TradeSortType.SortByTimestamp == trades.getTradeSortType()) {
                if (first.getTimestamp() == null) {
                    previous = now;
                    return;
                }
                UserTrade last = tradeList.get(tradeList.size() - 1);
                log.debug("first: {}, last: {}", first.getTimestamp(), last.getTimestamp());
                previous = first.getTimestamp().compareTo(last.getTimestamp()) < 0 ? last.getTimestamp() : first.getTimestamp();
            } else {
                if (first.getTimestamp() == null)
                    previous = now;
                else {
                    long max = first.getTimestamp().getTime();
                    for (UserTrade userTrade : tradeList)
                        max = Math.max(max, userTrade.getTimestamp().getTime());
                    previous = new Date(max);
                }
            }
        }
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
