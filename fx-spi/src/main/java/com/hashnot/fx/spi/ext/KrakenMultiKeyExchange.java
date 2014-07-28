package com.hashnot.fx.spi.ext;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.streaming.ExchangeStreamingConfiguration;
import com.xeiam.xchange.service.streaming.StreamingExchangeService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class KrakenMultiKeyExchange extends AbstractExchange {
    private List<Exchange> exchanges;

    public KrakenMultiKeyExchange(List<ExchangeSpecification> exchanges, IFeeService feeService, Map<String, BigDecimal> walletUnit, Map<String, BigDecimal> minimumOrder, int limitPriceScale, int tradeAmountScale) {
        super(feeService, walletUnit, minimumOrder, limitPriceScale, tradeAmountScale);
        this.exchanges = new ArrayList<>(exchanges.size());
        for (ExchangeSpecification spec : exchanges)
            this.exchanges.add(ExchangeFactory.INSTANCE.createExchange(spec));
    }

    protected Exchange getExchange(int i) {
        return exchanges.get(i % exchanges.size());
    }

    @Override
    public PollingMarketDataService getPollingMarketDataService() {
        return getExchange(0).getPollingMarketDataService();
    }

    @Override
    public StreamingExchangeService getStreamingExchangeService(ExchangeStreamingConfiguration configuration) {
        return getExchange(1).getStreamingExchangeService(configuration);
    }

    @Override
    public PollingTradeService getPollingTradeService() {
        return getExchange(2).getPollingTradeService();
    }

    @Override
    public PollingAccountService getPollingAccountService() {
        return getExchange(3).getPollingAccountService();
    }

    @Override
    protected void updateWallet() throws IOException {
        updateWallet(getExchange(4));
    }

    @Override
    public String toString() {
        return exchanges.get(0).toString();
    }
}
