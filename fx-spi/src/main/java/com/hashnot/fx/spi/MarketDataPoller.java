package com.hashnot.fx.spi;

import com.hashnot.fx.util.ExchangeUtil;
import com.lmax.disruptor.EventHandler;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * @author Rafał Krupiński
 */
public class MarketDataPoller implements EventHandler<OrderBookUpdateEvent> {
    final private static Logger log= LoggerFactory.getLogger(MarketDataPoller.class);
    private final PollingMarketDataService service;
    private final Collection<CurrencyPair> pairs;
    private final Map<CurrencyPair, OrderBook> cache = new HashMap<>();
    final private Exchange exchange;

    private int ordinal;
    private int count;

    public MarketDataPoller(PollingMarketDataService service, Collection<CurrencyPair> allowedPairs, Exchange exchange, int ordinal, int count) throws IOException {
        this.ordinal = ordinal;
        this.count = count;
        assert service != null : "null service";
        assert allowedPairs != null : "null pairs";
        assert !allowedPairs.isEmpty() : "empty pairs";
        assert exchange != null : "null exchange";

        this.service = service;
        this.exchange = exchange;

        Collection<CurrencyPair> _pairs = new LinkedList<>(ExchangeUtil.getCurrencyPairs(service));
        _pairs.retainAll(allowedPairs);
        pairs = Collections.unmodifiableCollection(_pairs);
    }

    @Override
    public void onEvent(OrderBookUpdateEvent evt, long sequence, boolean endOfBatch) throws Exception {
        if (sequence % count != ordinal) return;
        doOnEvent(evt, sequence, endOfBatch);
    }

    protected void doOnEvent(OrderBookUpdateEvent evt, long sequence, boolean endOfBatch) throws IOException {
        for (CurrencyPair pair : pairs) {
            OrderBook orderBook = service.getOrderBook(pair);

            OrderBook cached = cache.get(pair);
            if (cached != null) {
                if (equals(cached, orderBook)) {
                    log.debug("Drop unchanged order book from {}", exchange);
                    evt.setOrderBook(null);
                    return;
                }
            }
            cache.put(pair, orderBook);

            evt.setCurrencyPair(pair);
            evt.setOrderBook(orderBook);
            evt.setExchange(exchange);
        }
    }

    private static boolean equals(OrderBook o1, OrderBook o2) {
        return o1.getAsks().equals(o2.getAsks()) && o1.getBids().equals(o2.getBids());
    }
}

