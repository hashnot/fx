package com.hashnot.fx.spi.ext;

import com.hashnot.fx.ext.IOrderBookMonitor;
import com.hashnot.fx.ext.ITradesMonitor;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import com.xeiam.xchange.service.streaming.ExchangeStreamingConfiguration;
import com.xeiam.xchange.service.streaming.StreamingExchangeService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public interface IExchange {
    /**
     * @return scale (number of )
     */
    int getScale(CurrencyPair pair);

    /**
     * @return 10^{@link #getScale(com.xeiam.xchange.currency.CurrencyPair)}
     */
    BigDecimal getLimitPriceUnit(CurrencyPair pair);

    BigDecimal getWalletUnit(String currency);

    BigDecimal getMinimumTrade(String currency);

    BigDecimal getTradeAmountUnit(CurrencyPair pair);

    BigDecimal getFeePercent(CurrencyPair pair);

    BigDecimal getWallet(String currency);

    Map<String, BigDecimal> getWallet();

    PollingMarketDataService getPollingMarketDataService();

    StreamingExchangeService getStreamingExchangeService(ExchangeStreamingConfiguration configuration);

    ITradeService getPollingTradeService();

    PollingAccountService getPollingAccountService();

    BigDecimal getLimit(String currency);

    void start();

    void stop();

    ITradesMonitor getUserTradesMonitor();

    ITradeMonitor getTrackingUserTradesMonitor();

    IOrderBookMonitor getOrderBookMonitor();

    void updateWallet() throws IOException;
}
