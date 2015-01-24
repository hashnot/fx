package com.hashnot.xchange.event;

import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.account.AsyncAccountService;
import com.hashnot.xchange.async.account.IAsyncAccountService;
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
import com.hashnot.xchange.event.trade.IUserTradesMonitor;
import com.hashnot.xchange.event.trade.impl.OpenOrdersMonitor;
import com.hashnot.xchange.event.trade.impl.OrderTracker;
import com.hashnot.xchange.event.trade.impl.UserTradesMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.TradeMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @author Rafał Krupiński
 */
public class ExchangeMonitor implements IExchangeMonitor, IAsyncExchange, IAccountInfoListener {
    final private static Logger log = LoggerFactory.getLogger(ExchangeMonitor.class);

    protected ScheduledFuture<?> exchangeScheduler;
    final protected RoundRobinScheduler runnableScheduler;

    final protected Exchange exchange;
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
    final protected IAsyncMarketDataService marketDataService;
    final protected IAccountInfoMonitor accountInfoMonitor;

    final protected AbstractPollingMonitor[] closeables;

    protected Map<CurrencyPair, ? extends TradeMetaData> metadata;
    protected AccountInfo accountInfo;

    public ExchangeMonitor(Exchange parent, ScheduledExecutorService executor, long rate) throws Exception {
        this.exchange = parent;
        this.executor = executor;
        this.rate = rate;

        runnableScheduler = new RoundRobinScheduler(executor);
        tradeService = new AsyncTradeService(runnableScheduler, exchange);
        accountService = new AsyncAccountService(runnableScheduler, exchange.getPollingAccountService());
        marketDataService = new AsyncMarketDataService(runnableScheduler, exchange.getPollingMarketDataService());

        openOrdersMonitor = new OpenOrdersMonitor(exchange, runnableScheduler);
        userTradesMonitor = new UserTradesMonitor(exchange, runnableScheduler);
        accountInfoMonitor = new AccountInfoMonitor(exchange, accountService);
        accountInfoMonitor.addAccountInfoListener(this);

        WalletMonitor walletMonitor = new WalletMonitor(exchange, accountInfoMonitor);
        tickerMonitor = new TickerMonitor(marketDataService, exchange, runnableScheduler);
        orderBookMonitor = new OrderBookMonitor(exchange, runnableScheduler, marketDataService);

        orderTracker = new OrderTracker(userTradesMonitor);
        walletTracker = new WalletTracker(orderTracker, walletMonitor);

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

    public TradeMetaData getMarketMetadata(CurrencyPair pair) {
        return metadata.get(pair);
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
        for (AbstractPollingMonitor closeable : closeables)
            closeable.disable();
    }

    @Override
    public String toString() {
        return "Monitor:" + getExchange().getExchangeSpecification().getExchangeName();
    }

    @Override
    public void start() throws Exception {
        exchange.init();
        exchangeScheduler = executor.scheduleAtFixedRate(runnableScheduler, 0, rate, TimeUnit.MILLISECONDS);
        Future<AccountInfo> accountInfoFuture = accountInfoMonitor.update();
        metadata = tradeService.getMetadata(null).get();
        accountInfoFuture.get();
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
    public IAsyncMarketDataService getMarketDataService() {
        return marketDataService;
    }
}
