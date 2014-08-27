package com.hashnot.fx.spi.ext;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.fx.OrderBookUpdateEvent;
import com.hashnot.fx.spi.IOrderBookListener;
import com.hashnot.fx.util.exec.IExecutorStrategy;
import com.hashnot.fx.util.exec.IExecutorStrategyFactory;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Rafał Krupiński
 */
public class OrderBookMonitor {
    final private static Logger log = LoggerFactory.getLogger(OrderBookMonitor.class);

    final private IExchange parent;

    final private IExecutorStrategy orderBookMonitor;

    final private Map<CurrencyPair, OrderBook> orderBooks;
    final private Multimap<CurrencyPair, OrderBookMonitorSettings> orderBookMonitors = Multimaps.newMultimap(new ConcurrentHashMap<>(), () -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

    public OrderBookMonitor(IExecutorStrategyFactory executorStrategyFactory, Map<CurrencyPair, OrderBook> orderBooks, IExchange parent) {
        this.orderBooks = orderBooks;
        this.parent = parent;

        orderBookMonitor = executorStrategyFactory.create(this::updateOrderBooks);
    }

    protected void updateOrderBooks() {
        // Remove not monitored order books.
        orderBooks.keySet().stream().filter(pair -> !orderBookMonitors.containsKey(pair)).forEach(orderBooks::remove);

        for (Map.Entry<CurrencyPair, Collection<OrderBookMonitorSettings>> e : orderBookMonitors.asMap().entrySet()) {
            try {
                CurrencyPair pair = e.getKey();
                OrderBook orderBook = parent.getPollingMarketDataService().getOrderBook(pair);
                boolean changed = parent.updateOrderBook(pair, orderBook);
                if (changed) {
                    OrderBookUpdateEvent evt = new OrderBookUpdateEvent(pair, orderBooks.get(pair), orderBook);
                    for (OrderBookMonitorSettings settings : e.getValue()) {
                        settings.listener.changed(evt);
                    }
                }
            } catch (IOException e1) {
                log.warn("Error", e1);
            }
        }
    }

    static class OrderBookMonitorSettings {
        public final BigDecimal maxAmount;
        public final BigDecimal maxValue;
        public final IOrderBookListener listener;

        OrderBookMonitorSettings(BigDecimal maxAmount, BigDecimal maxValue, IOrderBookListener listener) {
            this.maxAmount = maxAmount;
            this.maxValue = maxValue;
            this.listener = listener;
        }
    }

    public void addOrderBookListener(CurrencyPair pair, BigDecimal maxAmount, BigDecimal maxValue, IOrderBookListener orderBookListener) {
        OrderBookMonitorSettings settings = new OrderBookMonitorSettings(maxAmount, maxValue, orderBookListener);
        orderBookMonitors.put(pair, settings);
        if (!this.orderBookMonitor.isStarted())
            orderBookMonitor.start();
    }

    public void removeOrderBookListener(IOrderBookListener orderBookListener) {
        orderBookMonitors.entries().stream().filter(e -> e.getValue().listener.equals(orderBookListener)).forEach(e -> orderBookMonitors.remove(e.getKey(), e.getValue()));

        synchronized (orderBookMonitors) {
            if (orderBookMonitors.isEmpty())
                orderBookMonitor.stop();
        }
    }
}
