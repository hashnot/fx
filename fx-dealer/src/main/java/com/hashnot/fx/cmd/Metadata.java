package com.hashnot.fx.cmd;

import com.hashnot.fx.framework.IStrategy;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.xeiam.xchange.currency.CurrencyPair;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

/**
 * @author Rafał Krupiński
 */
public class Metadata implements IStrategy {
    @Override
    public void init(Collection<IExchangeMonitor> exchangeMonitors, Collection<CurrencyPair> pairs, Runnable exitHook) throws Exception {
        CountDownLatch count = new CountDownLatch(exchangeMonitors.size() * pairs.size());
        for (IExchangeMonitor monitor : exchangeMonitors) {
            for (CurrencyPair pair : pairs) {
                monitor.getAsyncExchange().getMetadataService().getMarketMetadata(pair, (future) -> {
                    count.countDown();
                    try {
                        System.out.println(future.get());
                    } catch (InterruptedException | ExecutionException e) {
                        System.out.println(e);
                    }
                });

            }
        }
        count.await();
        exitHook.run();
    }
}
