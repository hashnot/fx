package com.hashnot.xchange.event.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.xchange.event.IOrderBookListener;
import com.hashnot.xchange.event.IOrderBookMonitor;
import com.hashnot.xchange.event.OrderBookUpdateEvent;
import com.hashnot.xchange.event.impl.exec.RunnableScheduler;
import com.hashnot.xchange.ext.Market;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class OrderBookMonitor extends AbstractPollingMonitor implements Runnable, IOrderBookMonitor {
    final private static Logger log = LoggerFactory.getLogger(OrderBookMonitor.class);

    final private Exchange parent;

    final private Multimap<CurrencyPair, IOrderBookListener> orderBookListeners = Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);

    public OrderBookMonitor(Exchange parent, RunnableScheduler runnableScheduler) {
        super(runnableScheduler);
        this.parent = parent;
    }

    public void run() {
        for (Map.Entry<CurrencyPair, Collection<IOrderBookListener>> e : orderBookListeners.asMap().entrySet()) {
            try {
                CurrencyPair pair = e.getKey();
                log.debug("Getting order book from {}", parent);
                OrderBook orderBook = parent.getPollingMarketDataService().getOrderBook(pair);
                log.debug("Order book @{}", orderBook.getTimeStamp().getTime());
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
