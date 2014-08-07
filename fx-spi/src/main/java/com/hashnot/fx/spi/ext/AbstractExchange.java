package com.hashnot.fx.spi.ext;

import com.hashnot.fx.spi.IOrderBookListener;
import com.hashnot.fx.spi.IOrderListener;
import com.hashnot.fx.util.exec.IExecutorStrategyFactory;
import com.hashnot.fx.util.Numbers;
import com.hashnot.fx.util.OrderBooks;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.streaming.ExchangeStreamingConfiguration;
import com.xeiam.xchange.service.streaming.StreamingExchangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractExchange implements IExchange {
    final private static Logger log = LoggerFactory.getLogger(SimpleExchange.class);
    static private final BigDecimal TWO = new BigDecimal(2);
    final public Map<CurrencyPair, OrderBook> orderBooks = new HashMap<>();
    final public Map<String, BigDecimal> wallet = new HashMap<>();

    protected final BigDecimal limitPriceUnit;
    protected final IFeeService feeService;
    protected final int scale;
    protected final Map<String, BigDecimal> walletUnit;
    protected final Map<String, BigDecimal> minimumOrder;
    protected final BigDecimal tradeAmountUnit;
    protected final Map<String, LimitOrder> openOrders = new HashMap<>();

    protected final OrderBookMonitor orderBookMonitor;
    final protected OpenOrderMonitor openOrderMonitor;
    final protected CachingTradeService tradeService;

    public AbstractExchange(IFeeService feeService, IExecutorStrategyFactory executorStrategyFactory, Map<String, BigDecimal> walletUnit, Map<String, BigDecimal> minimumOrder, int limitPriceScale, int tradeAmountScale) {
        this.feeService = feeService;
        this.walletUnit = walletUnit;
        this.minimumOrder = minimumOrder != null ? minimumOrder : Collections.emptyMap();
        this.scale = limitPriceScale;

        limitPriceUnit = ONE.movePointLeft(this.scale);
        tradeAmountUnit = ONE.movePointLeft(tradeAmountScale);
        orderBookMonitor = new OrderBookMonitor(executorStrategyFactory, orderBooks, this);
        openOrderMonitor = new OpenOrderMonitor(executorStrategyFactory, openOrders, this);
        tradeService = new CachingTradeService(() -> getExchange().getPollingTradeService(), openOrders);
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
        return getExchange().toString();
    }

    @Override
    public void stop() {
        tradeService.cancelAll();
    }

    @Override
    public void start() {
        try {
            updateWallet();
        } catch (IOException e) {
            log.warn("Error while updating wallet @{}", this, e);
        }
    }

    protected abstract Exchange getExchange();

    @Override
    public PollingMarketDataService getPollingMarketDataService() {
        return getExchange().getPollingMarketDataService();
    }

    @Override
    public StreamingExchangeService getStreamingExchangeService(ExchangeStreamingConfiguration configuration) {
        return getExchange().getStreamingExchangeService(configuration);
    }

    @Override
    public PollingTradeService getPollingTradeService() {
        return tradeService;
    }

    @Override
    public PollingAccountService getPollingAccountService() {
        return getExchange().getPollingAccountService();
    }

    @Override
    public void updateWallet() throws IOException {
        updateWallet(getExchange());
    }

    protected void updateWallet(Exchange x) throws IOException {
        AccountInfo accountInfo = x.getPollingAccountService().getAccountInfo();
        for (com.xeiam.xchange.dto.trade.Wallet w : accountInfo.getWallets()) {
            if (Numbers.equals(w.getBalance(), ZERO)) continue;

            String currency = w.getCurrency();
            BigDecimal current = wallet.getOrDefault(currency, ZERO);
            if (current != null && current.equals(w.getBalance()))
                continue;
            wallet.put(currency, w.getBalance());
        }
    }

    @Override
    public boolean updateOrderBook(CurrencyPair orderBookPair, OrderBook orderBook) {
        OrderBooks.removeOverLimit(orderBook, getLimit(orderBookPair.baseSymbol), getLimit(orderBookPair.counterSymbol));
        OrderBook current = getOrderBook(orderBookPair);
        if (current == null || !OrderBooks.equals(current, orderBook)) {
            orderBooks.put(orderBookPair, orderBook);
            OrderBooks.updateNetPrices(this, orderBookPair);
            return true;
        } else
            return false;
    }

    @Override
    public void addOrderBookListener(CurrencyPair pair, BigDecimal maxAmount, BigDecimal maxValue, IOrderBookListener orderBookMonitor) {
        this.orderBookMonitor.addOrderBookListener(pair, maxAmount, maxValue, orderBookMonitor);
    }

    public void removeOrderBookListener(IOrderBookListener orderBookMonitor) {
        this.orderBookMonitor.removeOrderBookListener(orderBookMonitor);
    }

    @Override
    public void addOrderListener(CurrencyPair pair, IOrderListener tradeListener) {
        openOrderMonitor.addOrderListener(pair, tradeListener);
    }

}
