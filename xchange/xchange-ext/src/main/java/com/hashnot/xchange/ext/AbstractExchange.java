package com.hashnot.xchange.ext;

import com.hashnot.xchange.ext.trade.ITradeService;
import com.hashnot.xchange.ext.trade.TradeService;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.service.polling.MarketMetadataService;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
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

    private final ITradeService tradeService = new TradeService(this::getExchange);

    public PollingMarketDataService getPollingMarketDataService() {
        return getExchange().getPollingMarketDataService();
    }

    public MarketMetadataService getMarketMetadataService() {
        return getExchange().getMarketMetadataService();
    }

    public StreamingExchangeService getStreamingExchangeService(ExchangeStreamingConfiguration configuration) {
        return getExchange().getStreamingExchangeService(configuration);
    }

    public ITradeService getPollingTradeService() {
        return tradeService;
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
