package com.hashnot.fx.framework;

import com.hashnot.fx.dealer.Dealer;
import com.hashnot.fx.ext.Market;
import com.hashnot.fx.ext.MarketSide;
import com.hashnot.fx.ext.impl.BestOfferMonitor;
import com.hashnot.fx.ext.impl.OrderBookSideMonitor;
import com.hashnot.fx.ext.impl.TrackingTradesMonitor;
import com.hashnot.fx.spi.ext.CachingTradeService;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.spi.ext.NotifyingTradeService;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
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
        ThreadFactory tf = new ConfigurableThreadFactory("%d/%d");
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10, tf);
        Collection<IExchange> exchanges = load(args[0], scheduler);

        Simulation simulation = new Simulation();
        OrderManager orderManager = new OrderManager();
        BestOfferMonitor bestOfferMonitor = new BestOfferMonitor();
        OrderBookSideMonitor orderBookSideMonitor = new OrderBookSideMonitor();
        Dealer dealer = new Dealer(simulation, orderManager, orderBookSideMonitor);

        for (IExchange exchange : exchanges) {
            exchange.start();
            final Market market = new Market(exchange, pair);
            bestOfferMonitor.addBestOfferListener(dealer, new MarketSide(market, Order.OrderType.ASK));
            bestOfferMonitor.addBestOfferListener(dealer, new MarketSide(market, Order.OrderType.BID));
            TrackingTradesMonitor trackingTradesMonitor = new TrackingTradesMonitor(exchange.getUserTradesMonitor());
            CachingTradeService tradeService = new CachingTradeService(new NotifyingTradeService(exchange.getPollingTradeService()));
            exchange.setPollingTradeService(tradeService);
            trackingTradesMonitor.addTradeListener(orderManager);

            Runtime.getRuntime().addShutdownHook(new Thread(tradeService::cancelAll, exchange.toString() + "-cancel"));
        }
        report(exchanges);
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
