package com.hashnot.fx.spi.ext;

import com.google.common.base.Suppliers;
import com.hashnot.fx.ext.IOrderBookListener;
import com.hashnot.fx.ext.ITradesMonitor;
import com.hashnot.fx.ext.impl.TrackingTradesMonitor;
import com.hashnot.fx.ext.impl.UserTradesMonitor;
import com.hashnot.fx.util.OrderBooks;
import com.hashnot.fx.util.exec.IExecutorStrategy;
import com.hashnot.fx.util.exec.IExecutorStrategyFactory;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import com.xeiam.xchange.service.streaming.ExchangeStreamingConfiguration;
import com.xeiam.xchange.service.streaming.StreamingExchangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hashnot.fx.util.Numbers.BigDecimal.isZero;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractExchange implements IExchange {
    final private static Logger log = LoggerFactory.getLogger(AbstractExchange.class);
    static private final BigDecimal TWO = new BigDecimal(2);
    final public Map<CurrencyPair, OrderBook> orderBooks = new HashMap<>();
    final public Map<String, BigDecimal> wallet = new HashMap<>();

    protected final BigDecimal limitPriceUnit;
    protected final IFeeService feeService;
    protected final int scale;
    protected final Map<String, BigDecimal> walletUnit;
    protected final Map<String, BigDecimal> minimumOrder;
    protected final BigDecimal tradeAmountUnit;
    protected final Map<String, LimitOrder> openOrders = new ConcurrentHashMap<>();

    protected final OrderBookMonitor orderBookMonitor;
    final protected ITradesMonitor userTradesMonitor;
    final protected TrackingTradesMonitor trackingUserTradesMonitor;
    final protected CachingTradeService tradeService;

    final protected RoundRobinRunnable runnableScheduler = new RoundRobinRunnable();
    private final IExecutorStrategy executor;

    public AbstractExchange(IFeeService feeService, IExecutorStrategyFactory executorStrategyFactory, Map<String, BigDecimal> walletUnit, Map<String, BigDecimal> minimumOrder, int limitPriceScale, int tradeAmountScale) {
        this.feeService = feeService;
        this.walletUnit = walletUnit;
        this.minimumOrder = minimumOrder != null ? minimumOrder : Collections.emptyMap();
        this.scale = limitPriceScale;

        executor = executorStrategyFactory.create(runnableScheduler);

        limitPriceUnit = ONE.movePointLeft(this.scale);
        tradeAmountUnit = ONE.movePointLeft(tradeAmountScale);
        orderBookMonitor = new OrderBookMonitor(this, runnableScheduler, orderBooks);
        userTradesMonitor = new UserTradesMonitor(getPollingTradeService(), runnableScheduler);
        trackingUserTradesMonitor = new TrackingTradesMonitor(userTradesMonitor);

        //lazy evaluation
        tradeService = new CachingTradeService(Suppliers.memoize(() -> new NotifyingTradeService(getExchange().getPollingTradeService())), openOrders);

        //OrderBookTradeMonitor orderBookMonitor = new OrderBookTradeMonitor(getPollingTradeService());
        //addOrderBookListener(pair, BigDecimal.ONE, BigDecimal.ONE, orderBookMonitor);
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
        executor.start();
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
    public ITradeService getPollingTradeService() {
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

    @Override
    public ITradesMonitor getUserTradesMonitor() {
        return userTradesMonitor;
    }

    @Override
    public ITradeMonitor getTrackingUserTradesMonitor() {
        return trackingUserTradesMonitor;
    }

    protected void updateWallet(Exchange x) throws IOException {
        AccountInfo accountInfo = x.getPollingAccountService().getAccountInfo();
        for (com.xeiam.xchange.dto.trade.Wallet w : accountInfo.getWallets()) {
            if (isZero(w.getBalance())) continue;

            String currency = w.getCurrency();
            BigDecimal current = wallet.getOrDefault(currency, ZERO);
            if (current != null && current.equals(w.getBalance()))
                continue;
            wallet.put(currency, w.getBalance());
        }
    }

    @Override
    public boolean updateOrderBook(CurrencyPair orderBookPair, OrderBook orderBook) {
        OrderBook limited = OrderBooks.removeOverLimit(orderBook, getLimit(orderBookPair.baseSymbol), getLimit(orderBookPair.counterSymbol));
        OrderBook current = getOrderBook(orderBookPair);
        if (current == null || !OrderBooks.equals(current, limited)) {
            orderBooks.put(orderBookPair, limited);
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
}
