package com.hashnot.xchange.event.impl;

import com.hashnot.xchange.event.*;
import com.hashnot.xchange.event.impl.exec.IExecutorStrategy;
import com.hashnot.xchange.event.impl.exec.IExecutorStrategyFactory;
import com.hashnot.xchange.event.impl.exec.RoundRobinRunnable;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @author Rafał Krupiński
 */
public class ExchangeMonitor implements IExchangeMonitor {
    final private static Logger log = LoggerFactory.getLogger(ExchangeMonitor.class);

    final private Exchange exchange;

    protected final Map<CurrencyPair, MarketMetadata> metadata = new HashMap<>();

    final protected IOrderBookMonitor orderBookMonitor;
    final protected IUserTradesMonitor userTradesMonitor;
    final protected ITickerMonitor tickerMonitor;
    final private IOpenOrdersMonitor openOrdersMonitor;
    final private IWalletMonitor walletMonitor;

    final protected RoundRobinRunnable runnableScheduler = new RoundRobinRunnable();
    private final IExecutorStrategy executor;

    public ExchangeMonitor(Exchange parent, IExecutorStrategyFactory executorStrategyFactory) {
        this.exchange = parent;
        executor = executorStrategyFactory.create(runnableScheduler);

        Executor _executor = executor.getExecutor();
        orderBookMonitor = new OrderBookMonitor(exchange, runnableScheduler, _executor);
        userTradesMonitor = new UserTradesMonitor(exchange, runnableScheduler);
        tickerMonitor = new TickerMonitor(exchange, runnableScheduler, _executor);
        openOrdersMonitor = new OpenOrdersMonitor(exchange, runnableScheduler);
        walletMonitor = new WalletMonitor(exchange, runnableScheduler, _executor);
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
    public String toString() {
        return getExchange().toString();
    }

    @Override
    public void stop() {
        executor.stop();
    }

    @Override
    public void start() {
        getWalletMonitor().update();
        executor.start();
    }

    @Override
    public ITickerMonitor getTickerMonitor() {
        return tickerMonitor;
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

    @Override
    public IWalletMonitor getWalletMonitor() {
        return walletMonitor;
    }

    @Override
    public Exchange getExchange() {
        return exchange;
    }
}
