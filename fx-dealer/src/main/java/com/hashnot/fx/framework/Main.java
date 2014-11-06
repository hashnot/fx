package com.hashnot.fx.framework;

import com.hashnot.fx.dealer.Dealer;
import com.hashnot.fx.framework.impl.BestOfferMonitor;
import com.hashnot.fx.framework.impl.OrderTracker;
import com.hashnot.fx.util.ConfigurableThreadFactory;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.Market;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
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

/**
 * @author Rafał Krupiński
 */
public class Main {
    private static final CurrencyPair pair = CurrencyPair.BTC_EUR;

    public static void main(String[] args) throws IOException, InterruptedException {
        ThreadFactory tf = new ConfigurableThreadFactory();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(9, tf);
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown,"shutdown thread pool"));

        Collection<IExchangeMonitor> monitors = load(args[0], scheduler);
        Map<Exchange, IExchangeMonitor> monitorMap = new HashMap<>();

        Simulation simulation = new Simulation(monitorMap);
        OrderTracker orderTracker = new OrderTracker(monitorMap);
        OrderManager orderManager = new OrderManager();
        BestOfferMonitor bestOfferMonitor = new BestOfferMonitor(monitorMap);

        Dealer dealer = new Dealer(simulation, orderManager, bestOfferMonitor, orderTracker, monitorMap);

        for (IExchangeMonitor monitor : monitors) {
            Exchange exchange = monitor.getExchange();
            monitorMap.put(exchange, monitor);
            monitor.start();

            orderTracker.addTradeListener(orderManager, exchange);
        }
        for (Map.Entry<Exchange, IExchangeMonitor> e : monitorMap.entrySet()) {
            Exchange x = e.getKey();
            IExchangeMonitor monitor = e.getValue();

            final Market market = new Market(x, pair);
            bestOfferMonitor.addBestOfferListener(dealer, new MarketSide(market, Order.OrderType.ASK));
            bestOfferMonitor.addBestOfferListener(dealer, new MarketSide(market, Order.OrderType.BID));

            ITradeService tradeService = (ITradeService) x.getPollingTradeService();
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
