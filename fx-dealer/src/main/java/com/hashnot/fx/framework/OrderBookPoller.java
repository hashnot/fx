package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ExchangeUpdateEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;

/**
 * @author Rafał Krupiński
 */
public class OrderBookPoller implements Runnable {
    final private static Logger log = LoggerFactory.getLogger(OrderBookPoller.class);
    final private Exchange exchange;
    final private BlockingQueue<ExchangeUpdateEvent> outQueue;
    final private Collection<CurrencyPair> pairs;

    public OrderBookPoller(Exchange exchange, BlockingQueue<ExchangeUpdateEvent> outQueue, Collection<CurrencyPair> pairs) {
        this.exchange = exchange;
        this.outQueue = outQueue;
        this.pairs = pairs;
    }

    @Override
    public void run() {
        PollingMarketDataService service = exchange.getPollingMarketDataService();
        for (CurrencyPair pair : pairs) {
            try {
                OrderBook result = service.getOrderBook(pair);
                log.debug("Got {} asks and {} bids from {}", result.getAsks().size(), result.getBids().size(), exchange);
                outQueue.add(new ExchangeUpdateEvent(exchange, pair, result));
            } catch (IOException e) {
                log.error("could not poll for order book from {}", exchange, e);
            }
        }
    }
}
