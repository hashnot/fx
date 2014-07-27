package com.hashnot.fx.spi;

import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;

/**
 * @author Rafał Krupiński
 */
public class ExchangeUpdateEvent {
    public IExchange exchange;

    public CurrencyPair orderBookPair;
    public OrderBook orderBook;

    public ExchangeUpdateEvent(IExchange exchange, CurrencyPair orderBookPair, OrderBook orderBook) {
        this.exchange = exchange;
        this.orderBookPair = orderBookPair;
        this.orderBook = orderBook;
    }
}
