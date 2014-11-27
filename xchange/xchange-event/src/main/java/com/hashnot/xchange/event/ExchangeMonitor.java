package com.hashnot.xchange.event;

import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.IExecutorStrategy;
import com.hashnot.xchange.async.IExecutorStrategyFactory;
import com.hashnot.xchange.async.RoundRobinScheduler;
import com.hashnot.xchange.async.account.AsyncAccountService;
import com.hashnot.xchange.async.account.IAsyncAccountService;
import com.hashnot.xchange.async.market.AsyncMarketMetadataService;
import com.hashnot.xchange.async.market.IAsyncMarketMetadataService;
import com.hashnot.xchange.async.trade.AsyncTradeService;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.account.IWalletMonitor;
import com.hashnot.xchange.event.account.WalletMonitor;
import com.hashnot.xchange.event.account.WalletTracker;
import com.hashnot.xchange.event.market.IOrderBookMonitor;
import com.hashnot.xchange.event.market.ITickerMonitor;
import com.hashnot.xchange.event.market.impl.OrderBookMonitor;
import com.hashnot.xchange.event.market.impl.TickerMonitor;
import com.hashnot.xchange.event.trade.IOpenOrdersMonitor;
import com.hashnot.xchange.event.trade.IOrderTracker;
import com.hashnot.xchange.event.trade.IUserTradesMonitor;
import com.hashnot.xchange.event.trade.impl.OpenOrdersMonitor;
import com.hashnot.xchange.event.trade.impl.OrderTracker;
import com.hashnot.xchange.event.trade.impl.UserTradesMonitor;
import com.hashnot.xchange.ext.IExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * @author Rafał Krupiński
 */
public class ExchangeMonitor implements IExchangeMonitor, IAsyncExchange {
    final private static Logger log = LoggerFactory.getLogger(ExchangeMonitor.class);

    final protected IExecutorStrategy executor;
    final protected RoundRobinScheduler runnableScheduler = new RoundRobinScheduler();

    final protected IExchange exchange;

    final protected IOrderBookMonitor orderBookMonitor;
    final protected IUserTradesMonitor userTradesMonitor;
    final protected ITickerMonitor tickerMonitor;
    final protected IOpenOrdersMonitor openOrdersMonitor;
    final protected IWalletMonitor walletTracker;
    final protected OrderTracker orderTracker;
    final protected IAsyncTradeService tradeService;
    final protected IAsyncAccountService accountService;
    final protected IAsyncMarketMetadataService metadataService;

    protected final Map<CurrencyPair, MarketMetadata> metadata = new HashMap<>();

    public ExchangeMonitor(IExchange parent, IExecutorStrategyFactory executorStrategyFactory) {
        this.exchange = parent;
        executor = executorStrategyFactory.create(runnableScheduler);

        Executor _executor = executor.getExecutor();
        orderBookMonitor = new OrderBookMonitor(exchange, runnableScheduler, _executor);
        tickerMonitor = new TickerMonitor(exchange, runnableScheduler, _executor);
        openOrdersMonitor = new OpenOrdersMonitor(exchange, runnableScheduler);
        tradeService = new AsyncTradeService(runnableScheduler, exchange.getPollingTradeService());
        userTradesMonitor = new UserTradesMonitor(exchange, tradeService, runnableScheduler);
        accountService = new AsyncAccountService(runnableScheduler, exchange.getPollingAccountService());
        metadataService = new AsyncMarketMetadataService(runnableScheduler, exchange.getMarketMetadataService());

        orderTracker = new OrderTracker(userTradesMonitor);
        exchange.getPollingTradeService().addLimitOrderPlacedListener(orderTracker);

        IWalletMonitor walletMonitor = new WalletMonitor(exchange, runnableScheduler, _executor, accountService);
        walletTracker = new WalletTracker(orderTracker, walletMonitor);
    }

    public MarketMetadata getMarketMetadata(CurrencyPair pair) {
        return metadata.computeIfAbsent(pair, (p) -> {
            try {
                return metadataService.getMarketMetadata(p, null).get();
            } catch (InterruptedException e) {
                log.warn("Interrupted!", e);
                return null;
            } catch (ExecutionException e) {
                try {
                    throw e.getCause();
                } catch (Error | RuntimeException r) {
                    throw r;
                } catch (Throwable throwable) {
                    throw new IllegalStateException(throwable);
                }
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
        getWalletMonitor().update(Collections.emptyList());
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
        return walletTracker;
    }

    @Override
    public IExchange getExchange() {
        return exchange;
    }

    @Override
    public IAsyncExchange getAsyncExchange() {
        return this;
    }

    public IAsyncTradeService getTradeService() {
        return tradeService;
    }

    @Override
    public IOrderTracker getOrderTracker() {
        return orderTracker;
    }

    @Override
    public IAsyncAccountService getAccountService() {
        return accountService;
    }

    @Override
    public IAsyncMarketMetadataService getMetadataService() {
        return metadataService;
    }
}
