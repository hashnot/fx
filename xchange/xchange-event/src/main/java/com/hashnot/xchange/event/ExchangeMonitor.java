package com.hashnot.xchange.event;

import com.hashnot.xchange.async.IExecutorStrategy;
import com.hashnot.xchange.async.IExecutorStrategyFactory;
import com.hashnot.xchange.async.RoundRobinScheduler;
import com.hashnot.xchange.async.trade.AsyncTradeService;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.account.IWalletMonitor;
import com.hashnot.xchange.event.account.WalletMonitor;
import com.hashnot.xchange.event.market.IOrderBookMonitor;
import com.hashnot.xchange.event.market.ITickerMonitor;
import com.hashnot.xchange.event.market.impl.OrderBookMonitor;
import com.hashnot.xchange.event.market.impl.TickerMonitor;
import com.hashnot.xchange.event.trade.IOpenOrdersMonitor;
import com.hashnot.xchange.event.trade.IUserTradesMonitor;
import com.hashnot.xchange.event.trade.impl.OpenOrdersMonitor;
import com.hashnot.xchange.event.trade.impl.UserTradesMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @author Rafał Krupiński
 */
public class ExchangeMonitor implements IExchangeMonitor {
    final private static Logger log = LoggerFactory.getLogger(ExchangeMonitor.class);

    final protected IExecutorStrategy executor;
    final protected RoundRobinScheduler runnableScheduler = new RoundRobinScheduler();

    final private Exchange exchange;

    final protected IOrderBookMonitor orderBookMonitor;
    final protected IUserTradesMonitor userTradesMonitor;
    final protected ITickerMonitor tickerMonitor;
    final protected IOpenOrdersMonitor openOrdersMonitor;
    final protected IWalletMonitor walletMonitor;
    final protected IAsyncTradeService tradeService;

    protected final Map<CurrencyPair, MarketMetadata> metadata = new HashMap<>();

    public ExchangeMonitor(Exchange parent, IExecutorStrategyFactory executorStrategyFactory) {
        this.exchange = parent;
        executor = executorStrategyFactory.create(runnableScheduler);

        Executor _executor = executor.getExecutor();
        orderBookMonitor = new OrderBookMonitor(exchange, runnableScheduler, _executor);
        userTradesMonitor = new UserTradesMonitor(exchange, runnableScheduler);
        tickerMonitor = new TickerMonitor(exchange, runnableScheduler, _executor);
        openOrdersMonitor = new OpenOrdersMonitor(exchange, runnableScheduler);
        walletMonitor = new WalletMonitor(exchange, runnableScheduler, _executor);
        tradeService = new AsyncTradeService(runnableScheduler, exchange.getPollingTradeService());
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

    public IAsyncTradeService getTradeService() {
        return tradeService;
    }

}
