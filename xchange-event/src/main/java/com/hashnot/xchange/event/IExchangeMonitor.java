package com.hashnot.xchange.event;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;

/**
 * @author Rafał Krupiński
 */
public interface IExchangeMonitor {

    ITickerMonitor getTickerMonitor();

    IUserTradesMonitor getUserTradesMonitor();

    IOrderBookMonitor getOrderBookMonitor();

    IOpenOrdersMonitor getOpenOrdersMonitor();

    IWalletMonitor getWalletMonitor();

    MarketMetadata getMarketMetadata(CurrencyPair pair);

    void start();

    void stop();

    Exchange getExchange();
}
