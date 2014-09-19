package com.hashnot.fx.framework;

import com.hashnot.fx.dealer.Dealer;
import com.hashnot.fx.spi.ExchangeUpdateEvent;
import com.hashnot.fx.spi.IOrderListener;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.spi.ext.OrderBookTradeMonitor;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import groovy.lang.GroovyShell;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static com.hashnot.fx.util.Exchanges.report;

/**
 * @author Rafał Krupiński
 */
public class Main {
    private static final CurrencyPair pair = CurrencyPair.BTC_EUR;
    static Collection<CurrencyPair> allowedPairs = Arrays.asList(/*CurrencyPairUtil.EUR_PLN, CurrencyPair.BTC_PLN, */pair);

    public static void main(String[] args) throws IOException, InterruptedException {
        ThreadFactory tf = new ConfigurableThreadFactory("p-%d-t-%d");
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10, tf);
        Collection<IExchange> exchanges = load(args[0], scheduler);

        Map<Order.OrderType, OrderUpdateEvent> myOpenOrders = new HashMap<>();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> exchanges.parallelStream().forEach(IExchange::stop), "Clear"));

        int capacity = 2 << 8;
        BlockingQueue<ExchangeUpdateEvent> updates = new ArrayBlockingQueue<>(capacity);

        exchanges.parallelStream().forEach(IExchange::start);

        report(exchanges);

        IOrderListener orderClosedListener = new OrderClosedListener(myOpenOrders);

        Simulation simulation = new Simulation();
        OrderUpdater orderUpdater = new OrderUpdater(myOpenOrders);
        Dealer dealer = new Dealer(exchanges, simulation, orderUpdater);

        for (IExchange exchange : exchanges) {

            //exchange.addOrderBookListener(pair, BigDecimal.ONE, BigDecimal.ONE, dealer);
            exchange.addOrderBookListener(pair, BigDecimal.ONE, BigDecimal.ONE, new OrderBookTradeMonitor(exchange.getPollingTradeService()));

            exchange.addOrderListener(pair, orderClosedListener);
        }



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
