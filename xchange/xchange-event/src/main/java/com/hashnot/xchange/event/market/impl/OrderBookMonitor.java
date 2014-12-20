package com.hashnot.xchange.event.market.impl;

import com.hashnot.xchange.async.impl.RunnableScheduler;
import com.hashnot.xchange.async.market.IAsyncMarketDataService;
import com.hashnot.xchange.event.AbstractParametrizedMonitor;
import com.hashnot.xchange.event.market.IOrderBookListener;
import com.hashnot.xchange.event.market.IOrderBookMonitor;
import com.hashnot.xchange.event.market.OrderBookUpdateEvent;
import com.hashnot.xchange.ext.Market;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class OrderBookMonitor extends AbstractParametrizedMonitor<CurrencyPair, IOrderBookListener, OrderBookUpdateEvent, OrderBook> implements Runnable, IOrderBookMonitor, OrderBookMonitorMBean {
    final private Exchange exchange;
    final private IAsyncMarketDataService marketDataService;

    final private Map<CurrencyPair, Long> timestamps = new HashMap<>();

    public OrderBookMonitor(Exchange exchange, RunnableScheduler runnableScheduler, IAsyncMarketDataService marketDataService) {
        super(runnableScheduler, IOrderBookListener::orderBookChanged, exchange.getExchangeSpecification().getExchangeName());
        this.marketDataService = marketDataService;
        this.exchange = exchange;
    }

    @Override
    protected void getData(CurrencyPair pair, Consumer<Future<OrderBook>> consumer) {
        marketDataService.getOrderBook(pair, consumer);
    }

    @Override
    protected OrderBookUpdateEvent wrap(CurrencyPair pair, OrderBook orderBook) {
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
    public Map<String, String> getListeners() {
        return listeners.reportListeners();
    }

    @Override
    public void addOrderBookListener(IOrderBookListener orderBookListener, CurrencyPair pair) {
        listeners.addListener(pair, orderBookListener);
    }

    public void removeOrderBookListener(IOrderBookListener orderBookListener, CurrencyPair pair) {
        listeners.removeListener(pair, orderBookListener);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + exchange;
    }
}
