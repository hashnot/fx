package com.hashnot.fx.framework;

import com.hashnot.fx.dealer.Dealer;
import com.hashnot.fx.spi.MarketDataPoller;
import com.hashnot.fx.spi.OrderBookUpdateEvent;
import com.hashnot.fx.util.CurrencyPairUtil;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.xeiam.xchange.bitcurex.BitcurexExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.kraken.KrakenExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * @author Rafał Krupiński
 */
public class Main {
    final private static Logger log = LoggerFactory.getLogger(Main.class);
    static Collection<CurrencyPair> allowedPairs = Arrays.asList(/*CurrencyPairUtil.EUR_PLN, CurrencyPair.BTC_PLN, */CurrencyPair.BTC_EUR);

    public static void main(String[] args) throws IOException {
        Disruptor<OrderBookUpdateEvent> updater = new Disruptor<>(OrderBookUpdateEvent::new, 2 << 8, Executors.newCachedThreadPool());

        KrakenExchange krakenExchange = new KrakenExchange();
        krakenExchange.applySpecification(krakenExchange.getDefaultExchangeSpecification());
        MarketDataPoller krakenPoller = new MarketDataPoller(krakenExchange.getPollingMarketDataService(), allowedPairs, krakenExchange, 0, 2);

        BitcurexExchange bitcurexExchange = new BitcurexExchange();
        bitcurexExchange.applySpecification(bitcurexExchange.getDefaultExchangeSpecification());
        MarketDataPoller bitcurexPoller = new MarketDataPoller(bitcurexExchange.getPollingMarketDataService(), allowedPairs, bitcurexExchange, 1, 2);


        updater.handleEventsWith(krakenPoller, bitcurexPoller).
                handleEventsWith(new Dealer()/*, new OrderBookPrinter()*/);

        updater.start();

        Timer t = new Timer("Market poll timer", true);
        t.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                RingBuffer<OrderBookUpdateEvent> buf = updater.getRingBuffer();
                long seq = buf.next();
                buf.publish(seq);
            }
        }, 100, 1000);
    }

}

