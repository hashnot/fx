package com.hashnot.fx.spi.ext;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.fx.spi.IOrderBookListener;
import com.hashnot.fx.spi.ITradeListener;
import com.hashnot.fx.util.IExecutorStrategy;
import com.hashnot.fx.util.IExecutorStrategyFactory;
import com.hashnot.fx.util.Numbers;
import com.hashnot.fx.util.OrderBooks;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.streaming.ExchangeStreamingConfiguration;
import com.xeiam.xchange.service.streaming.StreamingExchangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

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
    protected final Map<String, Long> trades = new HashMap<>();

    protected ScheduledExecutorService scheduler;
    protected final IExecutorStrategy orderBookMonitor;

    private final Multimap<CurrencyPair, ITradeListener> tradeListeners = Multimaps.newMultimap(new ConcurrentHashMap<>(), () -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
    private final Map<CurrencyPair, BigDecimal> tradeListenerAmounts = new ConcurrentHashMap<>();
    private final IExecutorStrategy tradeMonitor;

    public AbstractExchange(IFeeService feeService, IExecutorStrategyFactory executorStrategyFactory, Map<String, BigDecimal> walletUnit, Map<String, BigDecimal> minimumOrder, int limitPriceScale, int tradeAmountScale) {
        this.feeService = feeService;
        this.walletUnit = walletUnit;
        this.minimumOrder = minimumOrder != null ? minimumOrder : Collections.emptyMap();
        this.scale = limitPriceScale;

        limitPriceUnit = ONE.movePointLeft(this.scale);
        tradeAmountUnit = ONE.movePointLeft(tradeAmountScale);
        orderBookMonitor = executorStrategyFactory.create(this::updateOrderBooks);
        tradeMonitor = executorStrategyFactory.create(this::updateOpenOrders);
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
    public String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        PollingTradeService tradeService = getPollingTradeService();
        String id = tradeService.placeLimitOrder(limitOrder);
        if (openOrders.containsKey(id))
            log.warn("ID {} present @{}", id, this);
        openOrders.put(id, limitOrder);
        return id;
    }

    @Override
    public boolean cancelOrder(String orderId) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        if (!openOrders.containsKey(orderId))
            throw new ExchangeException("Unknown order " + orderId);
        boolean result = getPollingTradeService().cancelOrder(orderId);
        openOrders.remove(orderId);
        return result;
    }

    @Override
    public void stop() {
        cancelAll();
    }

    public void cancelAll() {
        openOrders.forEach((k, v) -> {
            try {
                if (!cancelOrder(k))
                    log.warn("Unsuccessful cancel {}@{}", k, this);
            } catch (IOException e) {
                log.warn("Error while cancelling");
            }
        });
    }

    @Override
    public void start(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
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
        return getExchange().getPollingTradeService();
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

    private final Multimap<CurrencyPair, OrderBookMonitorSettings> orderBookMonitors = Multimaps.newMultimap(new ConcurrentHashMap<>(), () -> Collections.newSetFromMap(new ConcurrentHashMap<>()));


    static class OrderBookMonitorSettings {
        public final Order.OrderType type;
        public final BigDecimal maxAmount;
        public final BigDecimal maxValue;
        public final IOrderBookListener listener;

        OrderBookMonitorSettings(Order.OrderType type, BigDecimal maxAmount, BigDecimal maxValue, IOrderBookListener listener) {
            this.type = type;
            this.maxAmount = maxAmount;
            this.maxValue = maxValue;
            this.listener = listener;
        }
    }

    @Override
    public void addOrderBookListener(CurrencyPair pair, Order.OrderType type, BigDecimal maxAmount, BigDecimal maxValue, IOrderBookListener orderBookMonitor) {
        OrderBookMonitorSettings settings = new OrderBookMonitorSettings(type, maxAmount, maxValue, orderBookMonitor);
        orderBookMonitors.put(pair, settings);
        synchronized (orderBookMonitors) {
            setOrderBookMonitoringEnabled(true);
        }
    }

    public void removeOrderBookListener(IOrderBookListener orderBookMonitor) {
        orderBookMonitors.entries().stream().filter(e -> e.getValue().listener.equals(orderBookMonitor)).forEach(e -> orderBookMonitors.remove(e.getKey(), e.getValue()));

        synchronized (orderBookMonitors) {
            if (orderBookMonitors.isEmpty())
                setOrderBookMonitoringEnabled(false);
        }
    }

    @Override
    public void addTradeListener(CurrencyPair pair, ITradeListener tradeListener) {
        tradeListeners.put(pair, tradeListener);
        if (!tradeMonitor.isStarted())
            tradeMonitor.start(scheduler);
    }

    protected void setOrderBookMonitoringEnabled(boolean enabled) {
        if (enabled)
            orderBookMonitor.start(scheduler);
        else
            orderBookMonitor.stop();
    }

    protected void updateOrderBooks() {
        orderBooks.keySet().stream().filter(pair -> !orderBookMonitors.containsKey(pair)).forEach(orderBooks::remove);

        for (Map.Entry<CurrencyPair, Collection<OrderBookMonitorSettings>> e : orderBookMonitors.asMap().entrySet()) {
            try {
                OrderBook orderBook = getPollingMarketDataService().getOrderBook(e.getKey());
                boolean changed = updateOrderBook(e.getKey(), orderBook);
                if (changed) {
                    for (OrderBookMonitorSettings settings : e.getValue()) {
                        settings.listener.changed(OrderBooks.get(orderBook, settings.type));
                    }
                }
            } catch (IOException e1) {
                log.warn("Error", e1);
            }
        }
    }

    private void updateOpenOrders() {
        try {
            OpenOrders orders = getPollingTradeService().getOpenOrders();
            if (orders == null) return;

            List<LimitOrder> currentOrders = orders.getOpenOrders();
            Map<String, LimitOrder> currentOrdersMap = Maps.uniqueIndex(currentOrders, Order::getId);

            List<String> remove = new LinkedList<>();
            for (Map.Entry<String, LimitOrder> e : openOrders.entrySet()) {
                LimitOrder currentOrder = currentOrdersMap.get(e.getKey());
                LimitOrder storedOrder = e.getValue();
                if (currentOrder != null && Numbers.equals(currentOrder.getTradableAmount(), storedOrder.getTradableAmount()))
                    continue;

                for (ITradeListener tradeListener : tradeListeners.get(storedOrder.getCurrencyPair())) {
                    tradeListener.trade(storedOrder, currentOrder);
                }
                if (currentOrder == null) {
                    remove.add(e.getKey());
                }

            }
            remove.forEach(openOrders::remove);

        } catch (IOException e) {
            log.warn("Error getting open orders");
        }
    }

}
