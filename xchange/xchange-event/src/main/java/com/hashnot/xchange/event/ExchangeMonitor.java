package com.hashnot.xchange.event;

import com.hashnot.xchange.async.AsyncExchange;
import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.account.AsyncAccountService;
import com.hashnot.xchange.async.account.IAsyncAccountService;
import com.hashnot.xchange.async.impl.AsyncSupport;
import com.hashnot.xchange.async.impl.RoundRobinScheduler;
import com.hashnot.xchange.async.market.AsyncMarketDataService;
import com.hashnot.xchange.async.market.IAsyncMarketDataService;
import com.hashnot.xchange.async.trade.AsyncTradeService;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.account.*;
import com.hashnot.xchange.event.market.IOrderBookMonitor;
import com.hashnot.xchange.event.market.ITickerMonitor;
import com.hashnot.xchange.event.market.impl.OrderBookMonitor;
import com.hashnot.xchange.event.market.impl.TickerMonitor;
import com.hashnot.xchange.event.trade.IOpenOrdersMonitor;
import com.hashnot.xchange.event.trade.IOrderTracker;
import com.hashnot.xchange.event.trade.ITrackingTradeService;
import com.hashnot.xchange.event.trade.IUserTradesMonitor;
import com.hashnot.xchange.event.trade.impl.OpenOrdersMonitor;
import com.hashnot.xchange.event.trade.impl.OrderTracker;
import com.hashnot.xchange.event.trade.impl.TrackingTradeService;
import com.hashnot.xchange.event.trade.impl.UserTradesMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.meta.ExchangeMetaData;
import com.xeiam.xchange.dto.meta.MarketMetaData;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @author Rafał Krupiński
 */
public class ExchangeMonitor implements IExchangeMonitor, IAccountInfoListener {
    final private static Logger log = LoggerFactory.getLogger(ExchangeMonitor.class);

    protected ScheduledFuture<?> exchangeScheduler;
    final protected RoundRobinScheduler runnableScheduler;

    final protected Exchange exchange;
    private ScheduledExecutorService executor;
    private long rate;

    final protected IAsyncExchange asyncExchange;
    final protected OrderBookMonitor orderBookMonitor;
    final protected UserTradesMonitor userTradesMonitor;
    final protected TickerMonitor tickerMonitor;
    final protected OpenOrdersMonitor openOrdersMonitor;
    final protected IWalletMonitor walletTracker;
    final protected OrderTracker orderTracker;
    final protected IAccountInfoMonitor accountInfoMonitor;
    final protected TrackingTradeService trackingTradeService;

    final protected AbstractPollingMonitor[] closeables;

    protected ExchangeMetaData metadata;
    protected AccountInfo accountInfo;

    public ExchangeMonitor(Exchange parent, ScheduledExecutorService executor, long rate) throws Exception {
        this.exchange = parent;
        this.executor = executor;
        this.rate = rate;

        runnableScheduler = new RoundRobinScheduler(executor);
        IAsyncTradeService tradeService = new AsyncTradeService(runnableScheduler, exchange);
        IAsyncAccountService accountService = new AsyncAccountService(runnableScheduler, exchange.getPollingAccountService());
        IAsyncMarketDataService marketDataService = new AsyncMarketDataService(runnableScheduler, exchange.getPollingMarketDataService());
        asyncExchange = new AsyncExchange(exchange, accountService, marketDataService, tradeService);

        openOrdersMonitor = new OpenOrdersMonitor(asyncExchange, runnableScheduler);
        userTradesMonitor = new UserTradesMonitor(asyncExchange, runnableScheduler);
        accountInfoMonitor = new AccountInfoMonitor(exchange, accountService);
        accountInfoMonitor.addAccountInfoListener(this);

        WalletMonitor walletMonitor = new WalletMonitor(exchange, accountInfoMonitor);
        tickerMonitor = new TickerMonitor(marketDataService, exchange, runnableScheduler);
        orderBookMonitor = new OrderBookMonitor(exchange, runnableScheduler, marketDataService);

        orderTracker = new OrderTracker(userTradesMonitor);
        walletTracker = new WalletTracker(orderTracker, walletMonitor);
        trackingTradeService = new TrackingTradeService(asyncExchange);
        orderTracker.addTradeListener(trackingTradeService);

        tradeService.addLimitOrderPlacedListener(orderTracker);
        closeables = new AbstractPollingMonitor[]{openOrdersMonitor, userTradesMonitor, tickerMonitor, orderBookMonitor};

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

    public MarketMetaData getMarketMetadata(CurrencyPair pair) {
        return metadata.getMarketMetaDataMap().get(pair);
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
        monitored.forEach((k, v) -> asyncExchange.getTradeService().cancelOrder(k, (future) -> count.countDown()));
        try {
            count.await();
        } catch (InterruptedException e) {
            log.warn("Interrupted!", e);
        }
    }

    protected void stopAll() {
        for (AbstractPollingMonitor closeable : closeables)
            closeable.disable();
    }

    @Override
    public String toString() {
        return "Monitor:" + getExchange().getExchangeSpecification().getExchangeName();
    }

    @Override
    public void start() throws Exception {
        exchangeScheduler = executor.scheduleAtFixedRate(runnableScheduler, 0, rate, TimeUnit.MILLISECONDS);
        Future<Void> f = AsyncSupport.call(this::init, null, runnableScheduler);
        f.get();

        Future<AccountInfo> accountInfoFuture = accountInfoMonitor.update();
        metadata = asyncExchange.getTradeService().getMetadata(null).get();
        accountInfoFuture.get();
    }

    protected Void init() throws IOException {
        exchange.remoteInit();
        return null;
    }

    @Override
    public void onAccountInfo(AccountInfoEvent evt) {
        assert evt.source.equals(exchange);
        AccountInfo accountInfo = evt.accountInfo;
        assert accountInfo != null;
        this.accountInfo = accountInfo;
    }

    @Override
    public AccountInfo getAccountInfo() {
        return accountInfo;
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
    public Exchange getExchange() {
        return exchange;
    }

    @Override
    public IAsyncExchange getAsyncExchange() {
        return asyncExchange;
    }

    @Override
    public IOrderTracker getOrderTracker() {
        return orderTracker;
    }

    @Override
    public ITrackingTradeService getTrackingTradeService() {
        return trackingTradeService;
    }

}
