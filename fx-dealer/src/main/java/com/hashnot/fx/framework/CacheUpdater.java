package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ExchangeCache;
import com.hashnot.fx.spi.ExchangeUpdateEvent;
import com.hashnot.fx.util.OrderBooks;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.BlockingQueue;

/**
 * @author Rafał Krupiński
 */
public class CacheUpdater implements Runnable {
    final private static Logger log = LoggerFactory.getLogger(CacheUpdater.class);

    private static final BigDecimal LIMIT_MULTIPLIER = new BigDecimal(2);
    private final Map<Exchange, ExchangeCache> context;
    private final BlockingQueue<ExchangeUpdateEvent> inQueue;
    private final BlockingQueue<CacheUpdateEvent> outQueue;

    public CacheUpdater(Map<Exchange, ExchangeCache> context, BlockingQueue<ExchangeUpdateEvent> inQueue, BlockingQueue<CacheUpdateEvent> outQueue) {
        this.context = context;
        this.inQueue = inQueue;
        this.outQueue = outQueue;
    }

    private final ArrayList<ExchangeUpdateEvent> localQueue = new ArrayList<>(2 << 8);
    private final Set<Exchange> processedAccounts = new HashSet<>();
    private final Set<Exchange> processedOrderBooks = new HashSet<>();
    private final Set<String> affectedCurrencies = new HashSet<>();
    private final Set<CurrencyPair> affectedPairs = new HashSet<>();

    @Override
    public void run() {
        inQueue.drainTo(localQueue);
        log.debug("Got {} events", localQueue.size());

        for (int i = localQueue.size() - 1; i >= 0; i--) {
            ExchangeUpdateEvent evt = localQueue.get(i);

            if (evt.accountInfo != null) {
                if (processedAccounts.add(evt.exchange))
                    update(evt.exchange, evt.accountInfo);
            }
            try {
                if (evt.orderBook != null)
                    if (processedOrderBooks.add(evt.exchange))
                        update(evt.exchange, evt.orderBook, evt.orderBookPair);
            } catch (Throwable x) {
                log.warn("Error", x);
            }
        }

        clear();
    }

    protected void clear() {
        localQueue.clear();
        processedAccounts.clear();
        processedOrderBooks.clear();
        affectedCurrencies.clear();
        affectedPairs.clear();
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

        log.debug("Changed pairs @{}: {}", exchange, affectedPairs);

        if (!outQueue.offer(new CacheUpdateEvent(exchange, affectedPairs)))
            log.error("Could not add event to cache update queue");
    }

    private void update(Exchange exchange, OrderBook orderBook, CurrencyPair orderBookPair) {
        ExchangeCache x = context.get(exchange);
        Map<String, BigDecimal> limits = x.orderBookLimits;
        OrderBooks.removeOverLimit(orderBook, limits.get(orderBookPair.baseSymbol), limits.get(orderBookPair.counterSymbol));
        OrderBook current = x.orderBooks.get(orderBookPair);
        if (current == null || !OrderBooks.equals(current, orderBook)) {
            x.orderBooks.put(orderBookPair, orderBook);
            if (!outQueue.offer(new CacheUpdateEvent(exchange, Arrays.asList(orderBookPair))))
                log.error("Could not add event to cache update queue");
        }
    }

}
