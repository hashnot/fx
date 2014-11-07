package com.hashnot.xchange.event.impl;

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
import java.util.concurrent.Executor;

/**
 * @author Rafał Krupiński
 */
public class OrderBookMonitor extends AbstractParametrizedMonitor<CurrencyPair, IOrderBookListener, OrderBookUpdateEvent> implements Runnable, IOrderBookMonitor {
    final private static Logger log = LoggerFactory.getLogger(OrderBookMonitor.class);

    final private Exchange parent;

    public OrderBookMonitor(Exchange parent, RunnableScheduler runnableScheduler, Executor executor) {
        super(runnableScheduler, executor);
        this.parent = parent;
    }

    @Override
    protected OrderBookUpdateEvent getData(CurrencyPair pair) throws IOException {
        log.debug("Getting order book from {}", parent);
        OrderBook orderBook = parent.getPollingMarketDataService().getOrderBook(pair);
        log.debug("Order book @{}", orderBook.getTimeStamp().getTime());
        return new OrderBookUpdateEvent(new Market(parent, pair), orderBook);
    }

    @Override
    protected void notifyListener(IOrderBookListener listener, OrderBookUpdateEvent evt) {
        listener.orderBookChanged(evt);
    }

    @Override
    public void addOrderBookListener(IOrderBookListener orderBookListener, CurrencyPair pair) {
        addListener(orderBookListener, pair);
    }

    public void removeOrderBookListener(IOrderBookListener orderBookListener, CurrencyPair pair) {
        removeListener(orderBookListener, pair);
    }
}
