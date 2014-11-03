package com.hashnot.fx.ext.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.fx.ext.IOrderBookListener;
import com.hashnot.fx.ext.IOrderBookMonitor;
import com.hashnot.fx.ext.Market;
import com.hashnot.fx.ext.OrderBookUpdateEvent;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.spi.ext.RunnableScheduler;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class OrderBookMonitor extends AbstractPollingMonitor implements Runnable, IOrderBookMonitor {
    final private static Logger log = LoggerFactory.getLogger(OrderBookMonitor.class);

    final private IExchange parent;

    final private Multimap<CurrencyPair, IOrderBookListener> orderBookListeners = Multimaps.newMultimap(new HashMap<>(), () -> Collections.newSetFromMap(new HashMap<>()));

    public OrderBookMonitor(IExchange parent, RunnableScheduler runnableScheduler) {
        super(runnableScheduler);
        this.parent = parent;
    }

    public void run() {
        for (Map.Entry<CurrencyPair, Collection<IOrderBookListener>> e : orderBookListeners.asMap().entrySet()) {
            try {
                CurrencyPair pair = e.getKey();
                OrderBook orderBook = parent.getPollingMarketDataService().getOrderBook(pair);
                notifyListeners(pair, orderBook, e.getValue());
            } catch (IOException x) {
                log.warn("Error", x);
            }
        }
    }

    protected void notifyListeners(CurrencyPair pair, OrderBook orderBook, Collection<IOrderBookListener> listeners) {
        OrderBookUpdateEvent evt = new OrderBookUpdateEvent(new Market(parent, pair), orderBook);
        for (IOrderBookListener listener : listeners) {
            try {
                listener.orderBookChanged(evt);
            } catch (RuntimeException e1) {
                log.warn("Error", e1);
            }
        }
    }

    @Override
    public void addOrderBookListener(IOrderBookListener orderBookListener, CurrencyPair pair) {
        synchronized (orderBookListeners) {
            orderBookListeners.put(pair, orderBookListener);
            enable();
        }
    }

    public void removeOrderBookListener(IOrderBookListener orderBookListener, CurrencyPair pair) {
        synchronized (orderBookListeners) {
            orderBookListeners.get(pair).remove(orderBookListener);
            if (orderBookListeners.isEmpty())
                disable();
        }
    }
}
