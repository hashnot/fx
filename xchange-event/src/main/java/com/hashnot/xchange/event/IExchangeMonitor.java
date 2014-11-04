package com.hashnot.xchange.event;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public interface IExchangeMonitor {

    ITickerMonitor getTickerMonitor();

    IUserTradesMonitor getUserTradesMonitor();

    IOrderBookMonitor getOrderBookMonitor();

    IOpenOrdersMonitor getOpenOrdersMonitor();

    MarketMetadata getMarketMetadata(CurrencyPair pair);

    BigDecimal getWallet(String currency);

    Map<String, BigDecimal> getWallet();

    BigDecimal getLimit(String currency);

    void start();

    void stop();

    void updateWallet() throws IOException;

    Exchange getExchange();
}
