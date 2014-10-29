package com.hashnot.fx.ext.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.fx.ext.ITickerListener;
import com.hashnot.fx.ext.ITickerMonitor;
import com.hashnot.fx.ext.Market;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.spi.ext.RunnableScheduler;
import com.hashnot.fx.util.Numbers;
import com.xeiam.xchange.dto.marketdata.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * @author Rafał Krupiński
 */
public class TickerMonitor extends AbstractPollingMonitor implements ITickerMonitor {
    final private Logger log = LoggerFactory.getLogger(TickerMonitor.class);

    private final IExchange exchange;
    private final Multimap<Market, ITickerListener> listeners = Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);
    private final Map<Market, Ticker> previous = new HashMap<>();

    @Override
    public void run() {
        for (Map.Entry<Market, Collection<ITickerListener>> e : listeners.asMap().entrySet()) {
            try {
                Market market = e.getKey();
                Ticker ticker = exchange.getPollingMarketDataService().getTicker(market.listing);
                Ticker prev = previous.get(market);
                if (prev != null && eq(prev, ticker))
                    continue;
                previous.put(market, ticker);

                for (ITickerListener listener : e.getValue()) {
                    listener.ticker(ticker, exchange);
                }
            } catch (IOException x) {
                log.error("Error", x);
            }
        }
    }

    @Override
    public void addTickerListener(ITickerListener listener, Market market) {
        assert exchange == market.exchange;
        synchronized (listeners) {
            listeners.put(market, listener);
            enable();
        }
    }

    @Override
    public void removeTickerListener(ITickerListener listener, Market market) {
        assert exchange == market.exchange;
        synchronized (listeners) {
            listeners.remove(market, listener);
            if (listeners.isEmpty())
                disable();
        }
    }

    public TickerMonitor(IExchange exchange, RunnableScheduler scheduler) {
        super(scheduler);
        this.exchange = exchange;
    }

    private static boolean eq(Ticker t1, Ticker t2) {
        assert t1.getCurrencyPair().equals(t2.getCurrencyPair());
        return Numbers.eq(t1.getAsk(), t2.getAsk()) && Numbers.eq(t1.getBid(), t2.getBid());
    }
}
