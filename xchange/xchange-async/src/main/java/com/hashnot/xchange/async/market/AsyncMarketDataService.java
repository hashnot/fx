package com.hashnot.xchange.async.market;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.service.polling.PollingMarketDataService;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static com.hashnot.xchange.async.impl.AsyncSupport.call;

/**
 * @author Rafał Krupiński
 */
public class AsyncMarketDataService implements IAsyncMarketDataService {
    final private Executor executor;
    final private PollingMarketDataService service;

    public AsyncMarketDataService(Executor remoteExecutor, PollingMarketDataService service) {
        executor = remoteExecutor;
        this.service = service;
    }

    @Override
    public Future<Ticker> getTicker(CurrencyPair currencyPair, Consumer<Future<Ticker>> consumer, Object... args) {
        return call(() -> service.getTicker(currencyPair, args), consumer, executor);
    }

    @Override
    public Future<OrderBook> getOrderBook(CurrencyPair currencyPair, Consumer<Future<OrderBook>> consumer, Object... args) {
        return call(() -> service.getOrderBook(currencyPair, args), consumer, executor);
    }

    @Override
    public Future<Trades> getTrades(CurrencyPair currencyPair, Consumer<Future<Trades>> consumer, Object... args) {
        return call(() -> service.getTrades(currencyPair, args), consumer, executor);
    }
}
