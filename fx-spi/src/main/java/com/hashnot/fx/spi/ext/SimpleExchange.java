package com.hashnot.fx.spi.ext;

import com.hashnot.fx.spi.ConnectionException;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.streaming.ExchangeStreamingConfiguration;
import com.xeiam.xchange.service.streaming.StreamingExchangeService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class SimpleExchange implements IExchange {
    private final Exchange exchange;
    private final BigDecimal minIncrease;
    private final IFeeService feeService;
    private ScheduledExecutorService executor;
    private final int scale;
    final public Map<CurrencyPair, OrderBook> orderBooks = new HashMap<>();
    final public Map<String, BigDecimal> wallet = new HashMap<>();
    static private final BigDecimal TWO = new BigDecimal(2);

    public SimpleExchange(Exchange exchange, IFeeService feeService, int scale) {
        this.scale = scale;
        this.exchange = exchange;
        this.feeService = feeService;
        minIncrease = new BigDecimal(1).movePointLeft(this.scale);
    }

    @Override
    public int getScale(CurrencyPair pair) {
        return scale;
    }

    @Override
    public BigDecimal getMinIncrease(CurrencyPair pair) {
        return minIncrease;
    }

    @Override
    public BigDecimal getFeePercent(CurrencyPair pair) {
        return feeService.getFeePercent(pair);
    }

    @Override
    public ExchangeSpecification getExchangeSpecification() {
        return exchange.getExchangeSpecification();
    }

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
        return exchange.getDefaultExchangeSpecification();
    }

    @Override
    public void applySpecification(ExchangeSpecification exchangeSpecification) {
        exchange.applySpecification(exchangeSpecification);
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
    public Map<CurrencyPair, OrderBook> getOrderBooks() {
        return orderBooks;
    }

    @Override
    public BigDecimal getLimit(String currency) {
        return getWallet(currency).multiply(TWO);
    }

    @Override
    public OrderBook getOrderBook(CurrencyPair pair) {
        return orderBooks.get(pair);
    }

    @Override
    public Map<String, BigDecimal> getWallet() {
        return wallet;
    }

    @Override
    public BigDecimal getWallet(String currency) {
        return wallet.getOrDefault(currency, ZERO);
    }

    @Override
    public String toString() {
        return exchange.getExchangeSpecification().getExchangeName() + "#" + hashCode();
    }

    @Override
    public void stop() {

    }

    @Override
    public void start(ScheduledExecutorService scheduler) {
        try {
            updateWallet();
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    protected void updateWallet() throws IOException {
        AccountInfo accountInfo = exchange.getPollingAccountService().getAccountInfo();
        for (com.xeiam.xchange.dto.trade.Wallet w : accountInfo.getWallets()) {
            if (w.getBalance().compareTo(ZERO) == 0) continue;

            String currency = w.getCurrency();
            BigDecimal current = wallet.getOrDefault(currency, ZERO);
            if (current != null && current.equals(w.getBalance()))
                continue;
            wallet.put(currency, w.getBalance());
        }
    }

}
