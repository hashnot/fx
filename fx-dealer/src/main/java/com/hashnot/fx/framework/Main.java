package com.hashnot.fx.framework;

import com.hashnot.fx.IFeeService;
import com.hashnot.fx.dealer.Dealer;
import com.hashnot.fx.ext.BitcurextFeeService;
import com.hashnot.fx.ext.KrakenFeeServiceStub;
import com.hashnot.fx.spi.MarketDataPoller;
import com.hashnot.fx.spi.OrderBookUpdateEvent;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.bitcurex.BitcurexExchange;
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

        int count = 2;

        KrakenExchange krakenExchange = new KrakenExchange();
        krakenExchange.applySpecification(krakenExchange.getDefaultExchangeSpecification());
        MarketDataPoller krakenPoller = new MarketDataPoller(krakenExchange.getPollingMarketDataService(), allowedPairs, krakenExchange, 0, count);

        BitcurexExchange bitcurexExchange = new BitcurexExchange();
        bitcurexExchange.applySpecification(bitcurexExchange.getDefaultExchangeSpecification());
        MarketDataPoller bitcurexPoller = new MarketDataPoller(bitcurexExchange.getPollingMarketDataService(), allowedPairs, bitcurexExchange, 1, count);

        Exchange[] exchanges = new Exchange[]{krakenExchange, bitcurexExchange};


        Simulation simulation = new Simulation();
        for (Exchange exchange : exchanges) {
            simulation.add(exchange, Currencies.EUR, new BigDecimal(2000));
            simulation.add(exchange, Currencies.BTC, new BigDecimal(4));
        }

        Map<Exchange, IFeeService> feeServices = new HashMap<>();
        feeServices.put(krakenExchange, new KrakenFeeServiceStub());
        feeServices.put(bitcurexExchange, new BitcurextFeeService());

        Dealer dealer = new Dealer(feeServices, simulation);

        updater.handleEventsWith(krakenPoller, bitcurexPoller).
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
                for (int i = 0; i < count; i++)
                    buf.publish(buf.next());
            }
        }, 0, 100);
        Thread.sleep(60000);
        updater.halt();
        //dealer.simulation.report();
    }

}
