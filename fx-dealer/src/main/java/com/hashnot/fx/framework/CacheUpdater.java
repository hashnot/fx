package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ExchangeCache;
import com.hashnot.fx.spi.ExchangeUpdateEvent;
import com.hashnot.fx.util.OrderBooks;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Rafał Krupiński
 */
public class CacheUpdater implements Runnable {
    final private static Logger log = LoggerFactory.getLogger(CacheUpdater.class);

    private static final BigDecimal LIMIT_MULTIPLIER = new BigDecimal(2);
    private final Map<Exchange, ExchangeCache> context;
    private final BlockingQueue<ExchangeUpdateEvent> inQueue;
    private final ICacheUpdateListener listener;
    private final Map<Order.OrderType, OrderUpdateEvent> openOrders;

    public CacheUpdater(Map<Exchange, ExchangeCache> context, BlockingQueue<ExchangeUpdateEvent> inQueue, ICacheUpdateListener listener, Map<Order.OrderType, OrderUpdateEvent> openOrders) {
        this.context = context;
        this.inQueue = inQueue;
        this.listener = listener;
        this.openOrders = openOrders;
    }

    private final ArrayList<ExchangeUpdateEvent> localQueue = new ArrayList<>(2 << 8);
    private final Set<Exchange> processedAccounts = new HashSet<>();
    private final Set<Exchange> processedOrderBooks = new HashSet<>();
    private final Set<String> affectedCurrencies = new HashSet<>();
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
                if (evt.accountInfo != null) {
                    if (processedAccounts.add(evt.exchange))
                        update(evt.exchange, evt.accountInfo);
                }
                if (evt.orderBook != null)
                    if (processedOrderBooks.add(evt.exchange))
                        update(evt.exchange, evt.orderBook, evt.orderBookPair);
            }
        } catch (Throwable x) {
            log.warn("Error", x);
        }

        if (!affectedPairs.isEmpty()) {
            log.debug("Changed pairs {}", affectedPairs);
            listener.cacheUpdated(affectedPairs);
        }
    }

    private void update(Exchange exchange, AccountInfo accountInfo) {
        ExchangeCache x = context.get(exchange);

        for (com.xeiam.xchange.dto.trade.Wallet w : accountInfo.getWallets()) {
            BigDecimal current = x.wallet.get(w.getCurrency());
            if (current != null && current.equals(w.getBalance()))
                continue;
            x.wallet.put(w.getCurrency(), w.getBalance());
            x.orderBookLimits.put(w.getCurrency(), w.getBalance().multiply(LIMIT_MULTIPLIER));
            affectedCurrencies.add(w.getCurrency());
        }

        for (String currency : affectedCurrencies) {
            for (CurrencyPair pair : x.orderBooks.keySet()) {
                if (pair.baseSymbol.equals(currency) || pair.counterSymbol.equals(currency))
                    affectedPairs.add(pair);
            }
        }
    }

    private void update(Exchange exchange, OrderBook orderBook, CurrencyPair orderBookPair) {
        ExchangeCache x = context.get(exchange);
        Map<String, BigDecimal> limits = x.orderBookLimits;
        injectOpenOrders(exchange, orderBook);
        OrderBooks.removeOverLimit(orderBook, limits.get(orderBookPair.baseSymbol), limits.get(orderBookPair.counterSymbol));
        OrderBook current = x.orderBooks.get(orderBookPair);
        if (current == null || !OrderBooks.equals(current, orderBook)) {
            x.orderBooks.put(orderBookPair, orderBook);
            OrderBooks.updateNetPrices(x, orderBookPair);
            affectedPairs.add(orderBookPair);
        }
    }

    private void injectOpenOrders(Exchange exchange, OrderBook orderBook) {
        injectOpenOrders(exchange, orderBook, Order.OrderType.ASK);
        injectOpenOrders(exchange, orderBook, Order.OrderType.BID);
    }

    private void injectOpenOrders(Exchange exchange, OrderBook orderBook, Order.OrderType type) {
        OrderUpdateEvent orderUpdate = openOrders.get(type);
        if (orderUpdate!=null && orderUpdate.openExchange.equals(exchange)) {
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
        processedAccounts.clear();
        processedOrderBooks.clear();
        affectedCurrencies.clear();
        affectedPairs.clear();
    }

}
