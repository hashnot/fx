package com.hashnot.fx.spi.ext;

import com.hashnot.fx.spi.ConnectionException;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.streaming.ExchangeStreamingConfiguration;
import com.xeiam.xchange.service.streaming.StreamingExchangeService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class SimpleExchange implements IExchange {
    private final Exchange exchange;
    private final BigDecimal limitPriceUnit;
    private final IFeeService feeService;
    private ScheduledExecutorService executor;
    private final int scale;
    final public Map<CurrencyPair, OrderBook> orderBooks = new HashMap<>();
    final public Map<String, BigDecimal> wallet = new HashMap<>();
    static private final BigDecimal TWO = new BigDecimal(2);

    final private Map<String, BigDecimal> walletUnit;
    final private Map<String, BigDecimal> minimumOrder;
    final private BigDecimal tradeAmountUnit;

    public SimpleExchange(Exchange exchange, IFeeService feeService, Map<String, BigDecimal> walletUnit, Map<String, BigDecimal> minimumOrder, int limitPriceScale, int tradeAmountScale) {
        this.minimumOrder = minimumOrder != null ? minimumOrder : Collections.emptyMap();
        this.scale = limitPriceScale;
        this.exchange = exchange;
        this.feeService = feeService;
        this.walletUnit = walletUnit;
        limitPriceUnit = ONE.movePointLeft(this.scale);
        tradeAmountUnit = ONE.movePointLeft(tradeAmountScale);
    }

    @Override
    public int getScale(CurrencyPair pair) {
        return scale;
    }

    @Override
    public BigDecimal getLimitPriceUnit(CurrencyPair pair) {
        return limitPriceUnit;
    }

    @Override
    public BigDecimal getTradeAmountUnit(CurrencyPair pair) {
        return tradeAmountUnit;
    }

    @Override
    public BigDecimal getWalletUnit(String currency) {
        BigDecimal result = walletUnit.get(currency);
        if (result == null) throw new IllegalArgumentException("No wallet unit for " + currency + " @" + toString());
        return result;
    }

    @Override
    public BigDecimal getMinimumTrade(String currency) {
        return minimumOrder.getOrDefault(currency, ZERO);
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

    protected void cancelAll() {
        PollingTradeService tradeService = getPollingTradeService();
        OpenOrders openOrders = null;
        try {
            openOrders = tradeService.getOpenOrders();
            for (LimitOrder order : openOrders.getOpenOrders()) {
                tradeService.cancelOrder(order.getId());
            }
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    @Override
    public void start(ScheduledExecutorService scheduler) {
        Runtime.getRuntime().addShutdownHook(new Thread(this::cancelAll));
        this.executor = scheduler;
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
