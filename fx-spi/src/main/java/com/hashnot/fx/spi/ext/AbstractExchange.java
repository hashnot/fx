package com.hashnot.fx.spi.ext;

import com.google.common.base.Suppliers;
import com.hashnot.fx.ext.IOrderBookMonitor;
import com.hashnot.fx.ext.ITradesMonitor;
import com.hashnot.fx.ext.impl.OrderBookMonitor;
import com.hashnot.fx.ext.impl.TrackingTradesMonitor;
import com.hashnot.fx.ext.impl.UserTradesMonitor;
import com.hashnot.fx.util.exec.IExecutorStrategy;
import com.hashnot.fx.util.exec.IExecutorStrategyFactory;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import com.xeiam.xchange.service.polling.MarketMetadataService;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import com.xeiam.xchange.service.streaming.ExchangeStreamingConfiguration;
import com.xeiam.xchange.service.streaming.StreamingExchangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static com.hashnot.fx.util.Numbers.BigDecimal.isZero;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractExchange implements IExchange {
    final private static Logger log = LoggerFactory.getLogger(AbstractExchange.class);
    static private final BigDecimal TWO = new BigDecimal(2);
    final public Map<String, BigDecimal> wallet = new HashMap<>();

    protected final Map<CurrencyPair, MarketMetadata> metadata = new HashMap<>();

    final protected IOrderBookMonitor orderBookMonitor;
    final protected ITradesMonitor userTradesMonitor;
    final protected TrackingTradesMonitor trackingUserTradesMonitor;
    final protected CachingTradeService tradeService;

    final protected RoundRobinRunnable runnableScheduler = new RoundRobinRunnable();
    private final IExecutorStrategy executor;

    public AbstractExchange(IExecutorStrategyFactory executorStrategyFactory) {
        executor = executorStrategyFactory.create(runnableScheduler);

        orderBookMonitor = new OrderBookMonitor(this, runnableScheduler);
        userTradesMonitor = new UserTradesMonitor(this, runnableScheduler);
        trackingUserTradesMonitor = new TrackingTradesMonitor(userTradesMonitor);

        //lazy evaluation
        tradeService = new CachingTradeService(Suppliers.memoize(() -> new NotifyingTradeService(getExchange().getPollingTradeService())));

        //OrderBookTradeMonitor orderBookMonitor = new OrderBookTradeMonitor(getPollingTradeService());
        //addOrderBookListener(pair, BigDecimal.ONE, BigDecimal.ONE, orderBookMonitor);
    }

    public MarketMetadata getMarketMetadata(CurrencyPair pair) {

        return metadata.computeIfAbsent(pair, (p) -> {
            try {
                return getMarketMetadataService().getMarketMetadata(p);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Override
    public BigDecimal getWalletUnit(String currency) {
        throw new UnsupportedOperationException();
/*
        BigDecimal result = walletUnit.get(currency);
        if (result == null) throw new IllegalArgumentException("No wallet unit for " + currency + " @" + toString());
        return result;
*/
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

    public MarketMetadataService getMarketMetadataService() {
        return getExchange().getMarketMetadataService();
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

    @Override
    public IOrderBookMonitor getOrderBookMonitor() {
        return orderBookMonitor;
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
}
