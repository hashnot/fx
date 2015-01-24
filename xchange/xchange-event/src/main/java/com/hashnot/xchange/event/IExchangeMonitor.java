package com.hashnot.xchange.event;

import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.event.account.IWalletMonitor;
import com.hashnot.xchange.event.market.IOrderBookMonitor;
import com.hashnot.xchange.event.market.ITickerMonitor;
import com.hashnot.xchange.event.trade.IOpenOrdersMonitor;
import com.hashnot.xchange.event.trade.IOrderTracker;
import com.hashnot.xchange.event.trade.IUserTradesMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.trade.TradeMetaData;

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

    TradeMetaData getMarketMetadata(CurrencyPair pair);

    /**
     * @return cached AccountInfo, updated by AccountInfoMonitor
     */
    AccountInfo getAccountInfo();

    void start() throws Exception;

    void stop();

    Exchange getExchange();

    IAsyncExchange getAsyncExchange();

    boolean equals(Object o);
}
