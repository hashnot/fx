package com.hashnot.xchange.event;

import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.RoundRobinScheduler;
import com.hashnot.xchange.async.account.AsyncAccountService;
import com.hashnot.xchange.async.account.IAsyncAccountService;
import com.hashnot.xchange.async.market.AsyncMarketDataService;
import com.hashnot.xchange.async.market.AsyncMarketMetadataService;
import com.hashnot.xchange.async.market.IAsyncMarketDataService;
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
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @author Rafał Krupiński
 */
public class ExchangeMonitor implements IExchangeMonitor, IAsyncExchange {
    final private static Logger log = LoggerFactory.getLogger(ExchangeMonitor.class);

    protected ScheduledFuture<?> exchangeScheduler;
    final protected RoundRobinScheduler runnableScheduler;

    final protected IExchange exchange;
    private ScheduledExecutorService executor;
    private long rate;

    final protected OrderBookMonitor orderBookMonitor;
    final protected UserTradesMonitor userTradesMonitor;
    final protected TickerMonitor tickerMonitor;
    final protected OpenOrdersMonitor openOrdersMonitor;
    final protected IWalletMonitor walletTracker;
    final protected OrderTracker orderTracker;
    final protected IAsyncTradeService tradeService;
    final protected IAsyncAccountService accountService;
    final protected IAsyncMarketMetadataService metadataService;
    final protected IAsyncMarketDataService marketDataService;

    final protected Iterable<AbstractPollingMonitor> closeables;

    protected final Map<CurrencyPair, MarketMetadata> metadata = new HashMap<>();

    public ExchangeMonitor(IExchange parent, ScheduledExecutorService executor, long rate) {
        this.exchange = parent;
        this.executor = executor;
        this.rate = rate;

        runnableScheduler = new RoundRobinScheduler(exchange.getExchangeSpecification().getExchangeName());
        openOrdersMonitor = new OpenOrdersMonitor(exchange, runnableScheduler);
        tradeService = new AsyncTradeService(runnableScheduler, exchange.getPollingTradeService(), executor);
        userTradesMonitor = new UserTradesMonitor(exchange, tradeService, runnableScheduler);
        accountService = new AsyncAccountService(runnableScheduler, exchange.getPollingAccountService(), executor);
        metadataService = new AsyncMarketMetadataService(runnableScheduler, exchange.getMarketMetadataService(), executor);

        orderTracker = new OrderTracker(userTradesMonitor);
        exchange.getPollingTradeService().addLimitOrderPlacedListener(orderTracker);

        WalletMonitor walletMonitor = new WalletMonitor(exchange, runnableScheduler, executor, accountService);
        walletTracker = new WalletTracker(orderTracker, walletMonitor);

        marketDataService = new AsyncMarketDataService(runnableScheduler, exchange.getPollingMarketDataService(), executor);
        tickerMonitor = new TickerMonitor(marketDataService, exchange, runnableScheduler, executor);
        orderBookMonitor = new OrderBookMonitor(exchange, runnableScheduler, executor, marketDataService);

        closeables = Arrays.asList(openOrdersMonitor, userTradesMonitor, tickerMonitor, walletMonitor);
    }

    public MarketMetadata getMarketMetadata(CurrencyPair pair) {
        return metadata.computeIfAbsent(pair, (p) -> {
            try {
                Future<MarketMetadata> future = metadataService.getMarketMetadata(p, null);
                return future.get();
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

    /**
     * - shutdown all polling monitors,
     * - cancel all open orders,
     * - stop exchange scheduler
     */
    @Override
    public void stop() {
        log.info("Stopping monitor: {}", exchange);

        stopAll();
        cancelAll();
        exchangeScheduler.cancel(false);
    }

    protected void cancelAll() {
        log.info("Cancelling all orders");
        Map<String, LimitOrder> monitored = orderTracker.getMonitored();
        CountDownLatch count = new CountDownLatch(monitored.size());
        monitored.forEach((k, v) -> {
            tradeService.cancelOrder(k, (future) -> count.countDown());
        });
        try {
            count.await();
        } catch (InterruptedException e) {
            log.warn("Interrupted!", e);
        }
    }

    protected void stopAll(){
        closeables.forEach(AbstractPollingMonitor::disable);
    }

    @Override
    public String toString() {
        return getExchange().toString();
    }

    @Override
    public void start() {
        exchangeScheduler = executor.scheduleAtFixedRate(runnableScheduler, 0, rate, TimeUnit.MILLISECONDS);
        getWalletMonitor().update(Collections.emptyList());
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

    @Override
    public IAsyncMarketDataService getMarketDataService() {
        return marketDataService;
    }
}
