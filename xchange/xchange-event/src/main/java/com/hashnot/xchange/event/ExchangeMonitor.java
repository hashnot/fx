package com.hashnot.xchange.event;

import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.RoundRobinScheduler;
import com.hashnot.xchange.async.RunnableScheduler;
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

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.*;
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

    public ExchangeMonitor(IExchange parent, ScheduledExecutorService executor, long rate) throws Exception {
        this.exchange = parent;
        this.executor = executor;
        this.rate = rate;

        runnableScheduler = new RoundRobinScheduler(executor, exchange.getExchangeSpecification().getExchangeName());
        tradeService = new AsyncTradeService(executor, exchange.getPollingTradeService());
        accountService = new AsyncAccountService(executor, exchange.getPollingAccountService());
        metadataService = new AsyncMarketMetadataService(executor, exchange.getMarketMetadataService());
        marketDataService = new AsyncMarketDataService(executor, exchange.getPollingMarketDataService());

        openOrdersMonitor = new OpenOrdersMonitor(exchange, runnableScheduler);
        userTradesMonitor = new UserTradesMonitor(exchange, runnableScheduler);
        WalletMonitor walletMonitor = new WalletMonitor(exchange, runnableScheduler, accountService);
        tickerMonitor = new TickerMonitor(marketDataService, exchange, runnableScheduler);
        orderBookMonitor = new OrderBookMonitor(exchange, runnableScheduler, marketDataService);

        orderTracker = new OrderTracker(userTradesMonitor);
        walletTracker = new WalletTracker(orderTracker, walletMonitor);

        exchange.getPollingTradeService().addLimitOrderPlacedListener(orderTracker);
        closeables = Arrays.asList(openOrdersMonitor, userTradesMonitor, tickerMonitor, walletMonitor, orderBookMonitor);

        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        registerMBean(mBeanServer, "com.hashnot.xchange.async", runnableScheduler);
        //mBeanServer.registerMBean(openOrdersMonitor)
        registerMBean(mBeanServer, "com.hashnot.xchange.event", userTradesMonitor);
        registerMBean(mBeanServer, "com.hashnot.xchange.event", walletMonitor);
        registerMBean(mBeanServer, "com.hashnot.xchange.event", tickerMonitor);
        registerMBean(mBeanServer, "com.hashnot.xchange.event", orderBookMonitor);
        registerMBean(mBeanServer, "com.hashnot.xchange.event", orderTracker);
        //registerMBean(mBeanServer, "com.hashnot.xchange.event", walletTracker);
    }

    private void registerMBean(MBeanServer server, String domain, Object object) throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        Hashtable<String, String> properties = new Hashtable<>();
        properties.put("exchange", exchange.toString());
        properties.put("type", object.getClass().getSimpleName());
        server.registerMBean(object, new ObjectName(domain, properties));
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
        monitored.forEach((k, v) -> tradeService.cancelOrder(k, (future) -> count.countDown()));
        try {
            count.await();
        } catch (InterruptedException e) {
            log.warn("Interrupted!", e);
        }
    }

    protected void stopAll() {
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

    public RunnableScheduler getRunnableScheduler() {
        return runnableScheduler;
    }
}
