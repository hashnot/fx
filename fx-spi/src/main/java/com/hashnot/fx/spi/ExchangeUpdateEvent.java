package com.hashnot.fx.spi;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.OrderBook;

/**
 * @author Rafał Krupiński
 */
public class ExchangeUpdateEvent {
    public Exchange exchange;
    public AccountInfo accountInfo;

    public CurrencyPair orderBookPair;
    public OrderBook orderBook;

    public ExchangeUpdateEvent(Exchange exchange, AccountInfo accountInfo) {
        this.exchange = exchange;
        this.accountInfo = accountInfo;
    }

    public ExchangeUpdateEvent(Exchange exchange, CurrencyPair orderBookPair, OrderBook orderBook) {
        this.exchange = exchange;
        this.orderBookPair = orderBookPair;
        this.orderBook = orderBook;
    }
}
