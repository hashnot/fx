package com.hashnot.fx.framework;

import com.hashnot.fx.dealer.Dealer;
import com.hashnot.fx.dealer.GaussOrderOpenStrategy;
import com.hashnot.fx.dealer.SimpleOrderCloseStrategy;
import com.hashnot.fx.framework.impl.BestOfferMonitor;
import com.hashnot.fx.framework.impl.OrderTracker;
import com.hashnot.fx.util.ConfigurableThreadFactory;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.Market;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import groovy.lang.GroovyShell;

import java.io.File;
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

        Collection<IExchangeMonitor> monitors = load(args[0], scheduler);
        Map<Exchange, IExchangeMonitor> monitorMap = new HashMap<>();

        OrderTracker orderTracker = new OrderTracker(monitorMap);
        BestOfferMonitor bestOfferMonitor = new BestOfferMonitor(monitorMap);

        GaussOrderOpenStrategy orderOpenStrategy = new GaussOrderOpenStrategy();
        SimpleOrderCloseStrategy orderCloseStrategy = new SimpleOrderCloseStrategy();

        Dealer askDealer = new Dealer(bestOfferMonitor, orderTracker, monitorMap, ASK, pair, orderOpenStrategy, orderCloseStrategy);
        Dealer bidDealer = new Dealer(bestOfferMonitor, orderTracker, monitorMap, BID, pair, orderOpenStrategy, orderCloseStrategy);

        for (IExchangeMonitor monitor : monitors) {
            Exchange x = monitor.getExchange();
            monitorMap.put(x, monitor);
            monitor.start();

            ITradeService tradeService = (ITradeService) x.getPollingTradeService();
            Market market = new Market(x, pair);

            bestOfferMonitor.addBestOfferListener(askDealer, new MarketSide(market, ASK));
            tradeService.addLimitOrderPlacedListener(askDealer);

            bestOfferMonitor.addBestOfferListener(bidDealer, new MarketSide(market, BID));
            tradeService.addLimitOrderPlacedListener(bidDealer);

            tradeService.addLimitOrderPlacedListener(orderTracker);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                tradeService.cancelAll();
                monitor.stop();
            }, x.toString() + "-stop"));
        }
        report(monitors);
    }

    @SuppressWarnings("unchecked")
    public static Collection<IExchangeMonitor> load(String path, ScheduledExecutorService scheduler) {
        GroovyShell sh = new GroovyShell();
        sh.setVariable("executor", scheduler);
        try {
            return (Collection<IExchangeMonitor>) sh.evaluate(new File(path));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load setup script", e);
        }

    }

}
