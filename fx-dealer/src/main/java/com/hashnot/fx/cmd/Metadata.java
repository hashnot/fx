package com.hashnot.fx.cmd;

import com.hashnot.fx.framework.IStrategy;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.xeiam.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

/**
 * @author Rafał Krupiński
 */
public class Metadata implements IStrategy {
    final private static Logger log = LoggerFactory.getLogger(Metadata.class);

    @Inject
    @Named("exitHook")
    private Runnable exitHook;

    @Override
    public void init(Collection<IExchangeMonitor> exchangeMonitors, Collection<CurrencyPair> pairs) throws Exception {
        CountDownLatch count = new CountDownLatch(exchangeMonitors.size() * pairs.size());
        for (IExchangeMonitor monitor : exchangeMonitors) {
            monitor.getAsyncExchange().getTradeService().getMetadata((future) -> {
                count.countDown();
                try {
                    log.info("{}", future.get());
                } catch (InterruptedException | ExecutionException e) {
                    log.error("Error", e);
                }
            });
        }
        count.await();
        exitHook.run();
    }

    @Override
    public void destroy() {

    }
}
