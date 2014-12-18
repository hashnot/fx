package com.hashnot.xchange.async;

import com.hashnot.xchange.async.account.IAsyncAccountService;
import com.hashnot.xchange.async.market.IAsyncMarketDataService;
import com.hashnot.xchange.async.trade.IAsyncTradeService;

/**
 * @author Rafał Krupiński
 */
public interface IAsyncExchange {
    IAsyncTradeService getTradeService();

    IAsyncAccountService getAccountService();

    IAsyncMarketDataService getMarketDataService();
}
