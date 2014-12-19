package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.IStrategy;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.framework.impl.BestOfferMonitor;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;

import javax.inject.Inject;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Future;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;

/**
 * @author Rafał Krupiński
 */
public class PairStrategy implements IStrategy {

    @Inject
    MBeanServer mBeanServer;

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
            Dealer askDealer = new Dealer(bestOfferMonitor, monitorMap, new DealerConfig(ASK, pair), orderOpenStrategy, orderCloseStrategy);
            Dealer bidDealer = new Dealer(bestOfferMonitor, monitorMap, new DealerConfig(BID, pair), orderOpenStrategy, orderCloseStrategy);

            for (Exchange x : monitorMap.keySet()) {
                registerBestOfferListener(bestOfferMonitor, askDealer, x);
                registerBestOfferListener(bestOfferMonitor, bidDealer, x);
            }
        }
    }

    private static void registerBestOfferListener(BestOfferMonitor bestOfferMonitor, Dealer dealer, Exchange exchange) {
        DealerConfig config = dealer.getConfig();
        bestOfferMonitor.addBestOfferListener(dealer, new MarketSide(exchange, config.listing, config.side));
    }

}
