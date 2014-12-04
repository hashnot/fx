package com.hashnot.xchange.event.trade.impl;

import com.hashnot.xchange.async.RunnableScheduler;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.AbstractPollingMonitor;
import com.hashnot.xchange.event.trade.IUserTradesListener;
import com.hashnot.xchange.event.trade.IUserTradesMonitor;
import com.hashnot.xchange.event.trade.UserTradesEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.trade.DefaultTradeHistoryParamsTimeSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;

/**
 * @author Rafał Krupiński
 */
public class UserTradesMonitor extends AbstractPollingMonitor implements IUserTradesMonitor {
    final private Logger log = LoggerFactory.getLogger(UserTradesMonitor.class);

    private final Exchange exchange;
    final private IAsyncTradeService tradeService;

    private final Set<IUserTradesListener> listeners = new HashSet<>();

    private volatile Date previous;

    @Override
    public void run() {
        Date now = new Date();

        log.debug("Getting trades from {} since {}", tradeService, previous);
        tradeService.getTradeHistory(new DefaultTradeHistoryParamsTimeSpan(previous), (future) -> {
            try {
                UserTrades trades = future.get();
                log.debug("User trades {}", trades.getUserTrades().size());

                updatePreviousDate(trades, now);

                if (!trades.getUserTrades().isEmpty())
                    multiplex(listeners, new UserTradesEvent(trades, exchange), IUserTradesListener::trades);

            } catch (InterruptedException e) {
                log.warn("Interrupted!", e);
            } catch (ExecutionException e) {
                log.warn("Error from {}", tradeService, e.getCause());
            }
        });
        if (previous == null || previous.compareTo(now) < 0)
            previous = now;
    }

    protected void updatePreviousDate(UserTrades trades, Date now) {
        List<UserTrade> tradeList = trades.getUserTrades();

        if (tradeList.isEmpty()) {
            previous = now;
            return;
        }

        UserTrade first = tradeList.get(0);
        if (first.getTimestamp() == null) {
            previous = now;
            return;
        }

        if (tradeList.size() == 1) {
            previous = first.getTimestamp();
            return;
        }

        if (Trades.TradeSortType.SortByTimestamp == trades.getTradeSortType()) {
            UserTrade last = tradeList.get(tradeList.size() - 1);
            log.debug("first: {}, last: {}", first.getTimestamp(), last.getTimestamp());
            previous = first.getTimestamp().compareTo(last.getTimestamp()) < 0 ? last.getTimestamp() : first.getTimestamp();
        } else {
            long max = first.getTimestamp().getTime();
            for (UserTrade userTrade : tradeList)
                max = Math.max(max, userTrade.getTimestamp().getTime());
            previous = new Date(max);
        }
    }

    public UserTradesMonitor(Exchange exchange, IAsyncTradeService tradeService, RunnableScheduler scheduler) {
        super(scheduler);
        this.exchange = exchange;
        this.tradeService = tradeService;
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
