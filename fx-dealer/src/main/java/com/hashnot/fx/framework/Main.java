package com.hashnot.fx.framework;

import com.hashnot.fx.dealer.Dealer;
import com.hashnot.fx.spi.ExchangeUpdateEvent;
import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import groovy.lang.GroovyShell;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import static com.hashnot.fx.util.Exchanges.report;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author Rafał Krupiński
 */
public class Main {
    static Collection<CurrencyPair> allowedPairs = Arrays.asList(/*CurrencyPairUtil.EUR_PLN, CurrencyPair.BTC_PLN, */CurrencyPair.BTC_EUR);

    public static void main(String[] args) throws IOException, InterruptedException {
        Collection<IExchange> exchanges = load(args[0]);

        Map<Order.OrderType, OrderUpdateEvent> myOpenOrders = new HashMap<>();

        ThreadFactory tf = new ConfigurableThreadFactory("p-%d-t-%d");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            exchanges.parallelStream().forEach(IExchange::stop);
        }, "Clear"));

        int capacity = 2 << 8;
        BlockingQueue<ExchangeUpdateEvent> updates = new ArrayBlockingQueue<>(capacity);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10, tf);

        exchanges.parallelStream().forEach(x -> x.start(scheduler));
        report(exchanges);

        IOrderClosedListener orderClosedListener = new OrderClosedListener(myOpenOrders);

        for (IExchange exchange : exchanges) {
            scheduler.scheduleAtFixedRate(
                    new OrderBookPoller(exchange, updates, allowedPairs)
                    , 100, 170, MILLISECONDS);
            scheduler.scheduleAtFixedRate(new TradeMonitor(exchange, orderClosedListener, myOpenOrders), 0, 200, MILLISECONDS);
        }

        Simulation simulation = new Simulation();
        OrderUpdater orderUpdater = new OrderUpdater(myOpenOrders);
        Dealer dealer = new Dealer(exchanges, simulation, orderUpdater);

        scheduler.execute(new CacheUpdater(updates, dealer, myOpenOrders));
//        scheduler.scheduleAtFixedRate(new StatusMonitor(updates, cacheUpdateQueue, orderUpdates), 0, 200, MILLISECONDS);

/*
        Thread.sleep(60000);
        scheduler.shutdown();
        scheduler.awaitTermination(2, TimeUnit.SECONDS);
        scheduler.shutdownNow();
        report(exchanges);
*/
    }

    public static Collection<IExchange> load(String path) {
        GroovyShell sh = new GroovyShell();
        try {
            return (Collection<IExchange>) sh.evaluate(new File(path));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load setup script", e);
        }

    }

}
