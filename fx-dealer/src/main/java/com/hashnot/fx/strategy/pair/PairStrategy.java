package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.IStrategy;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.framework.impl.BestOfferMonitor;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.util.MDCRunnable;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;

import javax.inject.Inject;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Rafał Krupiński
 */
public class PairStrategy implements IStrategy {

    @Inject
    MBeanServer mBeanServer;

    @Inject
    ScheduledExecutorService executor;

    private Future<?> listenerHandle;

    @Override
    public void init(Collection<IExchangeMonitor> exchangeMonitors, Collection<CurrencyPair> pairs) throws Exception {
        HashMap<Exchange, IExchangeMonitor> monitorMap = new HashMap<>();

        List<Future<Map<String, BigDecimal>>> futures = new ArrayList<>(monitorMap.size());
        for (IExchangeMonitor monitor : exchangeMonitors) {
            monitorMap.put(monitor.getExchange(), monitor);
            futures.add(monitor.getWalletMonitor().update());
        }
        for (Future<Map<String, BigDecimal>> future : futures)
            future.get();

        BestOfferMonitor bestOfferMonitor = new BestOfferMonitor(monitorMap);

        mBeanServer.registerMBean(bestOfferMonitor, new ObjectName("com.hashnot.fx", "type", bestOfferMonitor.getClass().getSimpleName()));

        GaussOrderOpenStrategy orderOpenStrategy = new GaussOrderOpenStrategy();
        SimpleOrderCloseStrategy orderCloseStrategy = new SimpleOrderCloseStrategy();

        for (CurrencyPair pair : pairs) {
            for (Order.OrderType side : Order.OrderType.values()) {
                Dealer dealer = new Dealer(bestOfferMonitor, monitorMap, new DealerConfig(side, pair), orderOpenStrategy, orderCloseStrategy);
                PairEventManager listener = new PairEventManager(dealer);

                listenerHandle = executor.submit(new MDCRunnable(listener, side.toString() + '@' + pair + '@'));

                dealer.setListener(listener);

                for (Exchange x : monitorMap.keySet())
                    bestOfferMonitor.addBestOfferListener(listener, new MarketSide(x, pair, side));
            }
        }
    }

    @Override
    public void destroy() {
        listenerHandle.cancel(true);
    }

}
