package com.hashnot.fx.framework;

import com.hashnot.fx.dealer.Dealer;
import com.hashnot.fx.ext.BTCEFeeService;
import com.hashnot.fx.ext.StaticFeeService;
import com.hashnot.fx.spi.ExchangeCache;
import com.hashnot.fx.spi.ExchangeUpdateEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.anx.v2.ANXExchange;
import com.xeiam.xchange.bitcurex.BitcurexExchange;
import com.xeiam.xchange.btce.v3.BTCEExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.kraken.KrakenExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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

        Exchange anx = defaultExchange(new ANXExchange());
        context.put(anx, new ExchangeCache(new StaticFeeService(new BigDecimal(".002"))));

        setup(context);

        for (Exchange exchange : context.keySet()) {
            scheduler.scheduleAtFixedRate(
                    new OrderBookPoller(exchange, updates, allowedPairs)
                    , 100, 100, TimeUnit.MILLISECONDS)
//                    .run()
            ;
        }

        Simulation simulation = new Simulation(context);
        Dealer dealer = new Dealer(context, simulation, orderUpdates);
        scheduler.execute(new CacheUpdater(context, updates, dealer));
        scheduler.scheduleAtFixedRate(new StatusMonitor(updates, cacheUpdateQueue, orderUpdates), 0, 200, TimeUnit.MILLISECONDS);

        scheduler.execute(() -> {
            try {
                while (true) {
                    OrderUpdateEvent e = orderUpdates.take();
                    log.info("(Not) sending {}", e);
                }
            } catch (InterruptedException e) {
                log.error("Interrupted", e);
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


    private static int base;

    private static void setupWallet(ExchangeCache exchange) {
        BigDecimal multi = new BigDecimal(2);

        BigDecimal eur = new BigDecimal(++base * 1000);
        BigDecimal btc = new BigDecimal(base * 2);

        exchange.wallet.put(EUR, eur);
        exchange.wallet.put(BTC, btc);

        exchange.orderBookLimits.put(EUR, eur.multiply(multi));
        exchange.orderBookLimits.put(BTC, btc.multiply(multi));
    }

    protected static Exchange defaultExchange(Exchange exchange) {
        exchange.applySpecification(exchange.getDefaultExchangeSpecification());
        return exchange;
    }

}
