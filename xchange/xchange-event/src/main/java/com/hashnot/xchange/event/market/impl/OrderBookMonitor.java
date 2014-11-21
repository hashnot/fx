package com.hashnot.xchange.event.market.impl;

import com.hashnot.xchange.async.RunnableScheduler;
import com.hashnot.xchange.event.AbstractParametrizedMonitor;
import com.hashnot.xchange.event.market.IOrderBookListener;
import com.hashnot.xchange.event.market.IOrderBookMonitor;
import com.hashnot.xchange.event.market.OrderBookUpdateEvent;
import com.hashnot.xchange.ext.Market;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @author Rafał Krupiński
 */
public class OrderBookMonitor extends AbstractParametrizedMonitor<CurrencyPair, IOrderBookListener, OrderBookUpdateEvent> implements Runnable, IOrderBookMonitor {
    final private static Logger log = LoggerFactory.getLogger(OrderBookMonitor.class);

    final private Exchange exchange;

    final private Map<CurrencyPair, Long> timestamps = new HashMap<>();

    public OrderBookMonitor(Exchange exchange, RunnableScheduler runnableScheduler, Executor executor) {
        super(runnableScheduler, executor);
        this.exchange = exchange;
    }

    @Override
    protected OrderBookUpdateEvent getData(CurrencyPair pair) throws IOException {
        log.debug("Getting order book from {}", exchange);
        OrderBook orderBook = exchange.getPollingMarketDataService().getOrderBook(pair);
        Date timeStamp = orderBook.getTimeStamp();
        Long time = timeStamp == null ? null : timeStamp.getTime();
        log.debug("Order book @{}", time == null ? "-" : time);

        if (timeStamp != null) {
            Long put = timestamps.put(pair, time);
            if (time.equals(put))
                return null;
        }

        return new OrderBookUpdateEvent(new Market(exchange, pair), orderBook);
    }

    @Override
    protected void callListener(IOrderBookListener listener, OrderBookUpdateEvent evt) {
        listener.orderBookChanged(evt);
    }

    @Override
    public void addOrderBookListener(IOrderBookListener orderBookListener, CurrencyPair pair) {
        addListener(orderBookListener, pair);
    }

    public void removeOrderBookListener(IOrderBookListener orderBookListener, CurrencyPair pair) {
        removeListener(orderBookListener, pair);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + exchange;
    }
}
