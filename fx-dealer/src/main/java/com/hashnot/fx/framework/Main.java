package com.hashnot.fx.framework;

import com.hashnot.fx.dealer.Dealer;
import com.hashnot.fx.ext.BTCEFeeService;
import com.hashnot.fx.ext.StaticFeeService;
import com.hashnot.fx.spi.ext.IFeeService;
import com.hashnot.fx.spi.*;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.bitcurex.BitcurexExchange;
import com.xeiam.xchange.btce.v3.BTCEExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.kraken.KrakenExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static com.xeiam.xchange.currency.Currencies.BTC;
import static com.xeiam.xchange.currency.Currencies.EUR;

/**
 * @author Rafał Krupiński
 */
public class Main {
    final private static Logger log = LoggerFactory.getLogger(Main.class);
    static Collection<CurrencyPair> allowedPairs = Arrays.asList(/*CurrencyPairUtil.EUR_PLN, CurrencyPair.BTC_PLN, */CurrencyPair.BTC_EUR);

    public static void main(String[] args) throws IOException, InterruptedException {
        Map<Exchange, ExchangeCache> context = new HashMap<>();
        int capacity = 2 << 8;
        BlockingQueue<ExchangeUpdateEvent> updates = new ArrayBlockingQueue<>(capacity);
        BlockingQueue<CacheUpdateEvent> cacheUpdateQueue = new ArrayBlockingQueue<>(capacity);
        ArrayBlockingQueue<OrderUpdateEvent> orderUpdates = new ArrayBlockingQueue<>(capacity);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10, new ConfigurableThreadFactory());

        Exchange kraken = defaultExchange(new KrakenExchange());
        context.put(kraken, new ExchangeCache(new StaticFeeService(new BigDecimal(".002"))));

        Exchange btce = defaultExchange(new BTCEExchange());
        context.put(btce, new ExchangeCache(new BTCEFeeService()));

        Exchange bitcurex = defaultExchange(new BitcurexExchange());
        context.put(bitcurex, new ExchangeCache(new StaticFeeService(new BigDecimal(".004"))));

        setup(context);

        for (Exchange exchange : context.keySet()) {
            scheduler.scheduleAtFixedRate(
                    new OrderBookPoller(exchange, updates, allowedPairs)
                    , 100, 100, TimeUnit.MILLISECONDS)
//                    .run()
            ;
        }

        //TODO integrate CacheUpdater with Dealer
        scheduler.scheduleAtFixedRate(new CacheUpdater(context, updates, cacheUpdateQueue), 150, 100, TimeUnit.MILLISECONDS);
        Simulation simulation = new Simulation(context);
        scheduler.execute(new Dealer(context, simulation, cacheUpdateQueue, orderUpdates));
        scheduler.scheduleAtFixedRate(new StatusMonitor(updates, cacheUpdateQueue, orderUpdates), 0, 200, TimeUnit.MILLISECONDS);

        scheduler.execute(() -> {
            try {
                while (true) {
                    OrderUpdateEvent e = orderUpdates.take();
                    log.info("{}", e);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });


        Thread.sleep(10000);
        scheduler.shutdown();
        scheduler.awaitTermination(2, TimeUnit.SECONDS);
        scheduler.shutdownNow();
        simulation.report();
    }

    private static void setup(Map<Exchange, ExchangeCache> context) {
        for (Map.Entry<Exchange, ExchangeCache> e : context.entrySet()) {
            setupWallet(e.getValue());
        }
    }

    private static void setupWallet(ExchangeCache exchange) {
        BigDecimal eur = new BigDecimal(2000);
        exchange.wallet.put(EUR, eur);
        BigDecimal multi = new BigDecimal(2);
        exchange.orderBookLimits.put(EUR, eur.multiply(multi));
        BigDecimal btc = new BigDecimal(4);
        exchange.wallet.put(BTC, btc);
        exchange.orderBookLimits.put(BTC, btc.multiply(multi));
    }

    protected static Exchange defaultExchange(Exchange exchange) {
        exchange.applySpecification(exchange.getDefaultExchangeSpecification());
        return exchange;
    }

    private static void createExchange(Map<Exchange, IFeeService> feeServiceMap, Exchange exchange, IFeeService feeService) {
        defaultExchange(exchange);
        feeServiceMap.put(exchange, feeService);
    }
}
