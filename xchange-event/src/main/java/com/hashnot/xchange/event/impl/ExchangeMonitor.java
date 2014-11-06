package com.hashnot.xchange.event.impl;

import com.hashnot.xchange.event.*;
import com.hashnot.xchange.event.impl.exec.IExecutorStrategy;
import com.hashnot.xchange.event.impl.exec.IExecutorStrategyFactory;
import com.hashnot.xchange.event.impl.exec.RoundRobinRunnable;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static com.hashnot.xchange.ext.util.Numbers.BigDecimal.isZero;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class ExchangeMonitor implements IExchangeMonitor {
    final private static Logger log = LoggerFactory.getLogger(ExchangeMonitor.class);

    final private Exchange parent;

    // TODO strategy specific
    static private final BigDecimal TWO = new BigDecimal(2);

    // TODO wallet monitor
    final public Map<String, BigDecimal> wallet = new HashMap<>();

    protected final Map<CurrencyPair, MarketMetadata> metadata = new HashMap<>();

    final protected IOrderBookMonitor orderBookMonitor;
    final protected IUserTradesMonitor userTradesMonitor;
    final protected ITickerMonitor tickerMonitor;
    final private IOpenOrdersMonitor openOrdersMonitor;

    final protected RoundRobinRunnable runnableScheduler = new RoundRobinRunnable();
    private final IExecutorStrategy executor;

    public ExchangeMonitor(Exchange parent, IExecutorStrategyFactory executorStrategyFactory) {
        this.parent = parent;
        executor = executorStrategyFactory.create(runnableScheduler);

        orderBookMonitor = new OrderBookMonitor(getExchange(), runnableScheduler, executor.getExecutor());
        userTradesMonitor = new UserTradesMonitor(getExchange(), runnableScheduler);
        tickerMonitor = new TickerMonitor(getExchange(), runnableScheduler, executor.getExecutor());
        openOrdersMonitor = new OpenOrdersMonitor(getExchange(), runnableScheduler);
    }

    public MarketMetadata getMarketMetadata(CurrencyPair pair) {

        return metadata.computeIfAbsent(pair, (p) -> {
            try {
                return getExchange().getMarketMetadataService().getMarketMetadata(p);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Override
    public BigDecimal getLimit(String currency) {
        return getWallet(currency).multiply(TWO);
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
        executor.stop();
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

    @Override
    public ITickerMonitor getTickerMonitor() {
        return tickerMonitor;
    }

    @Override
    public void updateWallet() throws IOException {
        updateWallet(getExchange());
    }

    @Override
    public IUserTradesMonitor getUserTradesMonitor() {
        return userTradesMonitor;
    }

    @Override
    public IOrderBookMonitor getOrderBookMonitor() {
        return orderBookMonitor;
    }

    @Override
    public IOpenOrdersMonitor getOpenOrdersMonitor() {
        return openOrdersMonitor;
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
    public Exchange getExchange() {
        return parent;
    }
}
