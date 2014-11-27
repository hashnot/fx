package com.hashnot.fx.framework;

import com.hashnot.fx.framework.impl.BestOfferMonitor;
import com.hashnot.fx.strategy.pair.Dealer;
import com.hashnot.fx.strategy.pair.DealerConfig;
import com.hashnot.fx.strategy.pair.GaussOrderOpenStrategy;
import com.hashnot.fx.strategy.pair.SimpleOrderCloseStrategy;
import com.hashnot.fx.util.ConfigurableThreadFactory;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.trade.ITradeService;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import static com.hashnot.fx.util.Exchanges.report;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;

/**
 * @author Rafał Krupiński
 */
public class Main {
    private static final CurrencyPair pair = CurrencyPair.BTC_EUR;

    public static void main(String[] args) throws IOException, InterruptedException {
        ThreadFactory tf = new ConfigurableThreadFactory();
        Thread.setDefaultUncaughtExceptionHandler(ConfigurableThreadFactory.exceptionHandler);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10, tf);
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown, "shutdown thread pool"));

        Collection<IExchangeMonitor> monitors = Setup.load(args[0], scheduler);
        Map<Exchange, IExchangeMonitor> monitorMap = new HashMap<>();

        BestOfferMonitor bestOfferMonitor = new BestOfferMonitor(monitorMap);

        GaussOrderOpenStrategy orderOpenStrategy = new GaussOrderOpenStrategy();
        SimpleOrderCloseStrategy orderCloseStrategy = new SimpleOrderCloseStrategy();

        Dealer askDealer = new Dealer(bestOfferMonitor, monitorMap, new DealerConfig(ASK, pair), orderOpenStrategy, orderCloseStrategy);
        Dealer bidDealer = new Dealer(bestOfferMonitor, monitorMap, new DealerConfig(BID, pair), orderOpenStrategy, orderCloseStrategy);

        for (IExchangeMonitor monitor : monitors) {
            Exchange x = monitor.getExchange();
            monitorMap.put(x, monitor);
            monitor.start();

            ITradeService tradeService = (ITradeService) x.getPollingTradeService();

            registerBestOfferListener(bestOfferMonitor, askDealer, x);
            registerBestOfferListener(bestOfferMonitor, bidDealer, x);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                tradeService.cancelAll();
                monitor.stop();
            }, "stop-" + x));
        }
        report(monitors);
    }

    protected static void registerBestOfferListener(BestOfferMonitor bestOfferMonitor, Dealer dealer, Exchange exchange) {
        DealerConfig config = dealer.getConfig();
        bestOfferMonitor.addBestOfferListener(dealer, new MarketSide(exchange, config.listing, config.side));
    }

}
