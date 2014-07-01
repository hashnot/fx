package com.hashnot.fx.framework;

import com.hashnot.fx.spi.OrderBookUpdateEvent;
import com.lmax.disruptor.EventHandler;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class OrderBookPrinter implements EventHandler<OrderBookUpdateEvent> {
    final private static Logger log = LoggerFactory.getLogger(OrderBookPrinter.class);

    final private Map<Exchange, OrderBook> orderBooks = new HashMap<>();

    @Override
    public void onEvent(OrderBookUpdateEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.getOrderBook() == null) return;

        orderBooks.put(event.getExchange(), event.getOrderBook());

        Collection<OrderBook> orderBookList = orderBooks.values();
        new DepthChart().createChart(event.getCurrencyPair(), orderBookList.toArray(new OrderBook[orderBookList.size()]));

        OrderBook orderBook = event.getOrderBook();
        if (orderBook == null) return;
        log.info("{}", event.getExchange().getExchangeSpecification().getExchangeName());
        print(orderBook.getBids().subList(0, 5));
        print(orderBook.getAsks().subList(0, 5));
    }

    private void print(List<LimitOrder> asks) {
        for (LimitOrder ask : asks) {
            log.info("{}", ask);
        }
    }
}
