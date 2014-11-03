package com.hashnot.xchange.ext;

import com.hashnot.xchange.event.IOrderBookMonitor;
import com.hashnot.xchange.event.ITickerMonitor;
import com.hashnot.xchange.event.ITradesMonitor;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import com.xeiam.xchange.service.polling.MarketMetadataService;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.streaming.ExchangeStreamingConfiguration;
import com.xeiam.xchange.service.streaming.StreamingExchangeService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public interface IExchange {

    BigDecimal getWalletUnit(String currency);

    MarketMetadata getMarketMetadata(CurrencyPair pair);

    BigDecimal getWallet(String currency);

    Map<String, BigDecimal> getWallet();

    PollingMarketDataService getPollingMarketDataService();

    MarketMetadataService getMarketMetadataService();

    StreamingExchangeService getStreamingExchangeService(ExchangeStreamingConfiguration configuration);

    PollingTradeService getPollingTradeService();

    ITickerMonitor getTickerMonitor();

    PollingAccountService getPollingAccountService();

    BigDecimal getLimit(String currency);

    void start();

    void stop();

    ITradesMonitor getUserTradesMonitor();

    IOrderBookMonitor getOrderBookMonitor();

    void updateWallet() throws IOException;

    void setPollingTradeService(PollingTradeService tradeService);
}
