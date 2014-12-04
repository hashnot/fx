package com.hashnot.fx.cmd;

import com.hashnot.fx.framework.IStrategy;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.util.Tickers;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.trade.LimitOrder;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;

/**
 * @author Rafał Krupiński
 */
public class OrderVerifier implements IStrategy {
    @Inject
    @Named("exitHook")
    private Runnable exitHook;

    private CountDownLatch count;

    @Override
    public void init(Collection<IExchangeMonitor> exchangeMonitors, Collection<CurrencyPair> pairs) throws Exception {
        CurrencyPair pair = pairs.iterator().next();
        count = new CountDownLatch(exchangeMonitors.size());
        for (IExchangeMonitor mon : exchangeMonitors) {
            testExchange(pair, mon);
        }
        count.await();
        exitHook.run();
    }

    protected void testExchange(CurrencyPair pair, IExchangeMonitor mon) throws ExecutionException, InterruptedException, IOException {
        Future<Ticker> future = mon.getAsyncExchange().getMarketDataService().getTicker(pair, null);
        Ticker ticker = future.get();
        BigDecimal topPrice = Tickers.getPrice(ticker, ASK);
        MarketMetadata meta = mon.getMarketMetadata(pair);

        BigDecimal myPrice = topPrice.subtract(meta.getPriceStep());

        LimitOrder order = new LimitOrder.Builder(ASK, pair).tradableAmount(meta.getAmountMinimum()).limitPrice(myPrice).build();
        String orderId = mon.getAsyncExchange().getTradeService().placeLimitOrder(order);
    }

    private void afterPlaceLimitOrder(Future<String> future) {

    }
}
