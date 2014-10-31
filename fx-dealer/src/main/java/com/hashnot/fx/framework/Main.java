package com.hashnot.fx.framework;

import com.hashnot.fx.dealer.Dealer;
import com.hashnot.fx.ext.Market;
import com.hashnot.fx.ext.impl.BestOfferMonitor;
import com.hashnot.fx.ext.impl.TrackingTradesMonitor;
import com.hashnot.fx.spi.ext.CachingTradeService;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.spi.ext.NotifyingTradeService;
import com.xeiam.xchange.currency.CurrencyPair;
import groovy.lang.GroovyShell;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
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
        ThreadFactory tf = new ConfigurableThreadFactory("p-%d-t-%d");
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10, tf);
        Collection<IExchange> exchanges = load(args[0], scheduler);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> exchanges.parallelStream().forEach(IExchange::stop), "Clear"));

        Simulation simulation = new Simulation();
        OrderManager orderManager = new OrderManager();
        BestOfferMonitor bestOfferMonitor = new BestOfferMonitor();
        Dealer dealer = new Dealer(simulation, orderManager, bestOfferMonitor);

        for (IExchange exchange : exchanges) {
            exchange.start();
            bestOfferMonitor.addBestOfferListener(dealer, new Market(exchange, pair));
            TrackingTradesMonitor trackingTradesMonitor = new TrackingTradesMonitor(exchange.getUserTradesMonitor());
            exchange.setPollingTradeService(new CachingTradeService(() -> new NotifyingTradeService(exchange.getPollingTradeService())));
            trackingTradesMonitor.addTradeListener(orderManager);
        }
        report(exchanges);



/*
        Thread.sleep(60000);
        scheduler.shutdown();
        scheduler.awaitTermination(2, TimeUnit.SECONDS);
        scheduler.shutdownNow();
        report(exchanges);
*/
    }

    public static Collection<IExchange> load(String path, ScheduledExecutorService scheduler) {
        GroovyShell sh = new GroovyShell();
        sh.setVariable("executor", scheduler);
        try {
            return (Collection<IExchange>) sh.evaluate(new File(path));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load setup script", e);
        }

    }

}
