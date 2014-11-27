package com.hashnot.xchange.async.market;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.marketdata.Trades;

import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public interface IAsyncMarketDataService {
    /**
     * Get a ticker representing the current exchange rate
     *
     * @return The Ticker, null if some sort of error occurred. Implementers should log the error.
     */
    Future<Ticker> getTicker(CurrencyPair currencyPair, Consumer<Future<Ticker>> consumer, Object... args);

    /**
     * Get an order book representing the current offered exchange rates (market depth)
     *
     * @param args Optional arguments. Exchange-specific
     * @return The OrderBook, null if some sort of error occurred. Implementers should log the error.
     */
    Future<OrderBook> getOrderBook(CurrencyPair currencyPair, Consumer<Future<OrderBook>> consumer, Object... args);

    /**
     * Get the trades recently performed by the exchange
     *
     * @param args Optional arguments. Exchange-specific
     * @return The Trades, null if some sort of error occurred. Implementers should log the error.
     */
    Future<Trades> getTrades(CurrencyPair currencyPair, Consumer<Future<Trades>> consumer, Object... args);

}
