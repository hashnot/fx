package com.hashnot.fx.ext;

import com.xeiam.xchange.dto.marketdata.OrderBook;

/**
 * @author Rafał Krupiński
 */
public class OrderBookUpdateEvent extends Event<Market> {
    public final OrderBook orderBook;

    public OrderBookUpdateEvent(Market source, OrderBook orderBook) {
        super(source);
        this.orderBook = orderBook;
    }
}
