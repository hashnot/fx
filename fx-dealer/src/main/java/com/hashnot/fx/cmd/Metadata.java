package com.hashnot.fx.cmd;

import com.hashnot.fx.framework.IStrategy;
import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.impl.AsyncSupport;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.xeiam.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

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
        CountDownLatch count = new CountDownLatch(exchangeMonitors.size() * 2);
        for (IExchangeMonitor monitor : exchangeMonitors) {
            IAsyncExchange x = monitor.getAsyncExchange();
            x.getTradeService().getMetadata((future) -> {
                count.countDown();
                handleFuture(monitor, future);
            });
            x.getAccountService().getAccountInfo(future -> {
                count.countDown();
                handleFuture(monitor, future);
            });
        }
        count.await();
        exitHook.run();
    }

    protected static <T> void handleFuture(IExchangeMonitor monitor, Future<T> future) {
        try {
            T value = AsyncSupport.get(future);
            log.info("{} {}", monitor, value);
        } catch (IOException e) {
            log.error("Error from {}", monitor, e);
        }
    }

    @Override
    public void destroy() {

    }
}
