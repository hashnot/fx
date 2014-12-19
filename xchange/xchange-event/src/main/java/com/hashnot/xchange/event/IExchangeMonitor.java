package com.hashnot.xchange.event;

import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.event.account.IWalletMonitor;
import com.hashnot.xchange.event.market.IOrderBookMonitor;
import com.hashnot.xchange.event.market.ITickerMonitor;
import com.hashnot.xchange.event.trade.IOpenOrdersMonitor;
import com.hashnot.xchange.event.trade.IOrderTracker;
import com.hashnot.xchange.event.trade.IUserTradesMonitor;
import com.hashnot.xchange.ext.IExchange;
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

    IOrderTracker getOrderTracker();

    MarketMetadata getMarketMetadata(CurrencyPair pair);

    void start() throws Exception;

    void stop();

    IExchange getExchange();

    IAsyncExchange getAsyncExchange();
}
