package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ExchangeUpdateEvent;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.util.OrderBooks;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Rafał Krupiński
 */
public class CacheUpdater implements Runnable {
    final private static Logger log = LoggerFactory.getLogger(CacheUpdater.class);

    private final BlockingQueue<ExchangeUpdateEvent> inQueue;
    private final ICacheUpdateListener listener;
    private final Map<Order.OrderType, OrderUpdateEvent> openOrders;

    public CacheUpdater(BlockingQueue<ExchangeUpdateEvent> inQueue, ICacheUpdateListener listener, Map<Order.OrderType, OrderUpdateEvent> openOrders) {
        this.inQueue = inQueue;
        this.listener = listener;
        this.openOrders = openOrders;
    }

    private final ArrayList<ExchangeUpdateEvent> localQueue = new ArrayList<>(2 << 8);
    private final Set<IExchange> processedOrderBooks = new HashSet<>();
    private final Collection<CurrencyPair> affectedPairs = new ArrayList<>(2 << 4);

    @Override
    public void run() {
        try {
            while (true) {
                ExchangeUpdateEvent evt = inQueue.poll(100, TimeUnit.MILLISECONDS);
                if (evt == null) continue;
                localQueue.add(evt);
                inQueue.drainTo(localQueue);
                log.debug("Got {} events", localQueue.size());

                process();

                clear();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted", e);
        }
    }

    protected void process() {
        try {
            for (ExchangeUpdateEvent evt : localQueue) {
                if (evt.orderBook != null)
                    if (processedOrderBooks.add(evt.exchange)) {
                        injectOpenOrders(evt.exchange, evt.orderBook);
                        if (evt.exchange.updateOrderBook(evt.orderBookPair, evt.orderBook))
                            affectedPairs.add(evt.orderBookPair);
                    }
            }
        } catch (Throwable x) {
            log.warn("Error", x);
        }

        if (!affectedPairs.isEmpty()) {
            log.debug("Changed pairs {}", affectedPairs);
            listener.cacheUpdated(affectedPairs);
        }
    }

    private void injectOpenOrders(IExchange exchange, OrderBook orderBook) {
        injectOpenOrders(exchange, orderBook, Order.OrderType.ASK);
        injectOpenOrders(exchange, orderBook, Order.OrderType.BID);
    }

    private void injectOpenOrders(IExchange exchange, OrderBook orderBook, Order.OrderType type) {
        OrderUpdateEvent orderUpdate = openOrders.get(type);
        if (orderUpdate != null && orderUpdate.openExchange.equals(exchange)) {
            List<LimitOrder> limitOrders = OrderBooks.get(orderBook, type);
            int i = Collections.binarySearch(limitOrders, orderUpdate.openedOrder);
            if (i < 0)
                limitOrders.add(-i - 1, orderUpdate.openedOrder);
            else
                limitOrders.set(i, Orders.add(limitOrders.get(i), orderUpdate.openedOrder));
        }
    }

    protected void clear() {
        localQueue.clear();
        processedOrderBooks.clear();
        affectedPairs.clear();
    }

}
