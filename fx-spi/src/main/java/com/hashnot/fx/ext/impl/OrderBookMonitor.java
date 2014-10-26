package com.hashnot.fx.ext.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.fx.OrderBookUpdateEvent;
import com.hashnot.fx.ext.IOrderBookListener;
import com.hashnot.fx.ext.IOrderBookMonitor;
import com.hashnot.fx.ext.Market;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.spi.ext.RunnableScheduler;
import com.hashnot.fx.util.OrderBooks;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Rafał Krupiński
 */
public class OrderBookMonitor extends AbstractPollingMonitor implements Runnable, IOrderBookMonitor {
    final private static Logger log = LoggerFactory.getLogger(OrderBookMonitor.class);

    final private IExchange parent;

    final private Map<CurrencyPair, OrderBook> orderBooks = new ConcurrentHashMap<>();
    final private Multimap<CurrencyPair, IOrderBookListener> orderBookListeners = Multimaps.newMultimap(new ConcurrentHashMap<>(), () -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

    public OrderBookMonitor(IExchange parent, RunnableScheduler runnableScheduler) {
        super(runnableScheduler);
        this.parent = parent;
    }

    public void run() {
        // Remove not monitored order books.
        orderBooks.keySet().stream().filter(pair -> !orderBookListeners.containsKey(pair)).forEach(orderBooks::remove);

        for (Map.Entry<CurrencyPair, Collection<IOrderBookListener>> e : orderBookListeners.asMap().entrySet()) {
            try {
                CurrencyPair pair = e.getKey();
                OrderBook before = orderBooks.get(pair);
                OrderBook orderBook = parent.getPollingMarketDataService().getOrderBook(pair);
                boolean changed = updateOrderBook(pair, orderBook);
                if (changed) {
                    OrderBookUpdateEvent evt = new OrderBookUpdateEvent(parent, pair, before, orderBook);
                    for (IOrderBookListener listener : e.getValue()) {
                        listener.orderBookChanged(evt);
                    }
                }
            } catch (IOException e1) {
                log.warn("Error", e1);
            }
        }
    }

    @Override
    public void addOrderBookListener(IOrderBookListener orderBookListener, Market market) {
        if (market.exchange != parent)
            throw new IllegalArgumentException("Mismatched exchange");

        synchronized (orderBookListeners) {
            orderBookListeners.put(market.listing, orderBookListener);
            enable();
        }
    }

    public void removeOrderBookListener(IOrderBookListener orderBookListener, Market market) {
        if (market.exchange != parent)
            throw new IllegalArgumentException("Mismatched exchange");

        synchronized (orderBookListeners) {
            orderBookListeners.get(market.listing).remove(orderBookListener);
            if (orderBookListeners.isEmpty())
                disable();
        }
    }

    public boolean updateOrderBook(CurrencyPair orderBookPair, OrderBook orderBook) {
        // TODO OrderBook limited = OrderBooks.removeOverLimit(orderBook, getLimit(orderBookPair.baseSymbol), getLimit(orderBookPair.counterSymbol));
        OrderBook current = orderBooks.get(orderBookPair);
        if (current == null || !OrderBooks.equals(current, orderBook)) {
            orderBooks.put(orderBookPair, orderBook);
            return true;
        } else
            return false;
    }
}
