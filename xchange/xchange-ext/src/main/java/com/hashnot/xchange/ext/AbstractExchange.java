package com.hashnot.xchange.ext;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.service.polling.MarketMetadataService;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.streaming.ExchangeStreamingConfiguration;
import com.xeiam.xchange.service.streaming.StreamingExchangeService;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractExchange implements IExchange {
    protected abstract Exchange getExchange();

    /**
     * Client code declares if it's going to call remote methods on the exchange
     */
    protected abstract Exchange getExchange(boolean forRemote);

    public PollingMarketDataService getPollingMarketDataService() {
        return getExchange().getPollingMarketDataService();
    }

    public MarketMetadataService getMarketMetadataService() {
        return getExchange().getMarketMetadataService();
    }

    public StreamingExchangeService getStreamingExchangeService(ExchangeStreamingConfiguration configuration) {
        return getExchange().getStreamingExchangeService(configuration);
    }

    @Override
    public PollingTradeService getPollingTradeService() {
        return getExchange().getPollingTradeService();
    }

    public PollingAccountService getPollingAccountService() {
        return getExchange().getPollingAccountService();
    }

    public ExchangeSpecification getDefaultExchangeSpecification() {
        return getExchange().getDefaultExchangeSpecification();
    }

    @Override
    public void applySpecification(ExchangeSpecification exchangeSpecification) {
        getExchange().applySpecification(exchangeSpecification);
    }

    public ExchangeSpecification getExchangeSpecification() {
        return getExchange().getExchangeSpecification();
    }

    @Override
    public String toString() {
        return getExchange(false).toString();
    }
}
