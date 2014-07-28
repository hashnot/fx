package com.hashnot.fx.spi.ext;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.streaming.ExchangeStreamingConfiguration;
import com.xeiam.xchange.service.streaming.StreamingExchangeService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class SimpleExchange extends AbstractExchange {

    private final Exchange exchange;

    public SimpleExchange(Exchange exchange, IFeeService feeService, Map<String, BigDecimal> walletUnit, Map<String, BigDecimal> minimumOrder, int limitPriceScale, int tradeAmountScale) {
        super(feeService, walletUnit, minimumOrder, limitPriceScale, tradeAmountScale);
        this.exchange = exchange;
    }

    @Override
    public PollingMarketDataService getPollingMarketDataService() {
        return exchange.getPollingMarketDataService();
    }

    @Override
    public StreamingExchangeService getStreamingExchangeService(ExchangeStreamingConfiguration configuration) {
        return exchange.getStreamingExchangeService(configuration);
    }

    @Override
    public PollingTradeService getPollingTradeService() {
        return exchange.getPollingTradeService();
    }

    @Override
    public PollingAccountService getPollingAccountService() {
        return exchange.getPollingAccountService();
    }

    @Override
    public String toString() {
        return exchange.getExchangeSpecification().getExchangeName() + "#" + hashCode();
    }

    @Override
    protected void updateWallet() throws IOException {
        updateWallet(exchange);
    }

}
