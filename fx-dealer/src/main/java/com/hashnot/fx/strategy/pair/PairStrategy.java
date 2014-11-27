package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.IStrategy;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.framework.impl.BestOfferMonitor;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;

/**
 * @author Rafał Krupiński
 */
public class PairStrategy implements IStrategy {
    @Override
    public void init(Collection<IExchangeMonitor> exchangeMonitors, Iterable<CurrencyPair> pairs, Runnable exitHook) {
        Map<Exchange, IExchangeMonitor> monitorMap = map(exchangeMonitors);

        BestOfferMonitor bestOfferMonitor = new BestOfferMonitor(monitorMap);

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

    private static Map<Exchange, IExchangeMonitor> map(Iterable<IExchangeMonitor> monitors) {
        HashMap<Exchange, IExchangeMonitor> result = new HashMap<>();
        for (IExchangeMonitor monitor : monitors)
            result.put(monitor.getExchange(), monitor);
        return result;
    }

    private static void registerBestOfferListener(BestOfferMonitor bestOfferMonitor, Dealer dealer, Exchange exchange) {
        DealerConfig config = dealer.getConfig();
        bestOfferMonitor.addBestOfferListener(dealer, new MarketSide(exchange, config.listing, config.side));
    }

}
