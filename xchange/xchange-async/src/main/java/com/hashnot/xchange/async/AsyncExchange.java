package com.hashnot.xchange.async;

import com.hashnot.xchange.async.account.IAsyncAccountService;
import com.hashnot.xchange.async.market.IAsyncMarketDataService;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.xeiam.xchange.Exchange;

public class AsyncExchange implements IAsyncExchange {
    final protected String name;
    final protected IAsyncTradeService tradeService;
    final protected IAsyncAccountService accountService;
    final protected IAsyncMarketDataService marketDataService;
    final protected Exchange exchange;

    public AsyncExchange(Exchange exchange, IAsyncAccountService accountService, IAsyncMarketDataService marketDataService, IAsyncTradeService tradeService) {
        this.name = exchange.getExchangeSpecification().getExchangeName();
        this.tradeService = tradeService;
        this.accountService = accountService;
        this.marketDataService = marketDataService;
        this.exchange = exchange;
    }

    @Override
    public IAsyncTradeService getTradeService() {
        return tradeService;
    }

    @Override
    public IAsyncAccountService getAccountService() {
        return accountService;
    }

    @Override
    public IAsyncMarketDataService getMarketDataService() {
        return marketDataService;
    }

    @Override
    public Exchange getExchange() {
        return exchange;
    }

    @Override
    public String toString() {
        return name;
    }
}
