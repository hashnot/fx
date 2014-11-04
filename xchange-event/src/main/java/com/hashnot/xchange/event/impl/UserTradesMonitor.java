package com.hashnot.xchange.event.impl;

import com.hashnot.xchange.event.IUserTradesMonitor;
import com.hashnot.xchange.event.IUserTradesListener;
import com.hashnot.xchange.event.impl.exec.RunnableScheduler;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.kraken.KrakenAdapters;
import com.xeiam.xchange.kraken.service.polling.KrakenTradeService;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Rafał Krupiński
 */
public class UserTradesMonitor extends AbstractPollingMonitor implements IUserTradesMonitor {
    final private Logger log = LoggerFactory.getLogger(UserTradesMonitor.class);

    private final Exchange parent;

    private final Set<IUserTradesListener> listeners = new HashSet<>();

    private long previous = 0;

    @Override
    public void run() {
        try {
            // TODO exchange specific code
            PollingTradeService tradeService = parent.getPollingTradeService();
            UserTrades trades;
            if ("com.xeiam.xchange.anx.v2.service.polling.ANXTradeService".equals(tradeService.getClass().getName())) {

                trades = tradeService.getTradeHistory(previous);
                assert Trades.TradeSortType.SortByTimestamp == trades.getTradeSortType();
                List<UserTrade> userTrades = trades.getUserTrades();
                UserTrade first = userTrades.get(0);
                UserTrade last = userTrades.get(userTrades.size() - 1);
                log.info("first: {}, last: {}", first.getTimestamp(), last.getTimestamp());
                previous = last.getTimestamp().getTime();

            } else if (tradeService instanceof KrakenTradeService) {
                KrakenTradeService krakenTradeService = (KrakenTradeService) tradeService;
                trades = KrakenAdapters.adaptTradesHistory(krakenTradeService.getKrakenTradeHistory(null, false, previous, null, null));

                for (UserTrade trade : trades.getUserTrades()) {
                    previous = Math.max(previous, trade.getTimestamp().getTime());
                }
            } else {
                log.warn("Unsupported exchange {}", parent);
                return;
            }

            for (IUserTradesListener listener : this.listeners)
                try {
                    listener.trades(trades);
                } catch (RuntimeException e) {
                    log.warn("Error in {}", listener, e);
                }
        } catch (IOException e) {
            log.error("Error", e);
        }
    }

    public UserTradesMonitor(Exchange parent, RunnableScheduler scheduler) {
        super(scheduler);
        this.parent = parent;
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
