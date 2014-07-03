package com.hashnot.fx.framework;

import com.hashnot.fx.IFeeService;
import com.hashnot.fx.dealer.Dealer;
import com.hashnot.fx.ext.BTCEFeeService;
import com.hashnot.fx.ext.BitcurextFeeService;
import com.hashnot.fx.ext.StaticFeeService;
import com.hashnot.fx.spi.MarketDataPoller;
import com.hashnot.fx.spi.OrderBookUpdateEvent;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.bitcurex.BitcurexExchange;
import com.xeiam.xchange.btce.v3.BTCEExchange;
import com.xeiam.xchange.currency.Currencies;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.kraken.KrakenExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * @author Rafał Krupiński
 */
public class Main {
    final private static Logger log = LoggerFactory.getLogger(Main.class);
    static Collection<CurrencyPair> allowedPairs = Arrays.asList(/*CurrencyPairUtil.EUR_PLN, CurrencyPair.BTC_PLN, */CurrencyPair.BTC_EUR);

    public static void main(String[] args) throws IOException, InterruptedException {
        Disruptor<OrderBookUpdateEvent> updater = new Disruptor<>(OrderBookUpdateEvent::new, 2 << 8, Executors.newCachedThreadPool());

        Map<Exchange, EventHandler<OrderBookUpdateEvent>> handlers = new HashMap<>();
        Map<Exchange, IFeeService> feeServices = new HashMap<>();

        createExchange(feeServices, new KrakenExchange(), new StaticFeeService(new BigDecimal(".002")));
        createExchange(feeServices, new BitcurexExchange(), new BitcurextFeeService());
        createExchange(feeServices, new BTCEExchange(), new BTCEFeeService());

        createMarket(handlers, feeServices);

        Simulation simulation = new Simulation(feeServices);

        for (Exchange exchange : handlers.keySet()) {
            simulation.add(exchange, Currencies.EUR, new BigDecimal(2000));
            simulation.add(exchange, Currencies.BTC, new BigDecimal(4));
        }

        Dealer dealer = new Dealer(feeServices, simulation);

        updater.handleEventsWith(handlers.values().toArray(new EventHandler[handlers.size()])).
                handleEventsWith(
                        dealer
                /*
                , new OrderBookPrinter()
                */
                );

        updater.start();

        Timer t = new Timer("Market poll timer", true);
        t.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                RingBuffer<OrderBookUpdateEvent> buf = updater.getRingBuffer();
                //for (int i = 0; i < handlers.size(); i++)
                buf.publish(buf.next());
            }
        }, 0, 100);
        Thread.sleep(60000);
        updater.halt();
        simulation.report();
    }

    private static void createExchange(Map<Exchange, IFeeService> feeServiceMap, Exchange exchange, IFeeService feeService) {
        exchange.applySpecification(exchange.getDefaultExchangeSpecification());
        feeServiceMap.put(exchange, feeService);
    }

    private static void createMarket(Map<Exchange, EventHandler<OrderBookUpdateEvent>> handlers, Map<Exchange, IFeeService> feeServiceMap) throws IOException {
        int i = 0;
        for (Map.Entry<Exchange, IFeeService> e : feeServiceMap.entrySet()) {
            Exchange exchange = e.getKey();
            handlers.put(exchange, new MarketDataPoller(exchange.getPollingMarketDataService(), allowedPairs, exchange, i++, feeServiceMap.size()));
        }
    }

}
