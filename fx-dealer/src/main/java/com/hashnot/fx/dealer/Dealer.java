package com.hashnot.fx.dealer;

import com.hashnot.fx.spi.OrderBookUpdateEvent;
import com.lmax.disruptor.EventHandler;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

/**
 * @author Rafał Krupiński
 */
public class Dealer implements EventHandler<OrderBookUpdateEvent> {
    private Map<CurrencyPair, Map<Exchange, OrderBook>> orders = new HashMap<>();

    private static void printReport(CurrencyPair pair, Map<Exchange, OrderBook> books, Order.OrderType dir) {
        SortedMap<LimitOrder, Exchange> asks = getBestOffers(books, dir);
        log.info("{} summary: best {} {}", pair, asks.firstKey(), asks.get(asks.firstKey()));
        log.info("{} summery: wors {} {}", pair, asks.lastKey(), asks.get(asks.lastKey()));
    }

    private static SortedMap<LimitOrder, Exchange> getBestOffers(Map<Exchange, OrderBook> books, Order.OrderType dir) {
        SortedMap<LimitOrder, Exchange> result = new TreeMap<>();
        for (Map.Entry<Exchange, OrderBook> e : books.entrySet()) {
            List<LimitOrder> limitOrders = get(e.getValue(), dir);
            if (limitOrders.isEmpty()) continue;
            result.put(limitOrders.get(0), e.getKey());
        }
        return result;
    }

    private static List<LimitOrder> get(OrderBook orderBook, Order.OrderType dir) {
        return dir == Order.OrderType.ASK ? orderBook.getAsks() : orderBook.getBids();
    }

    @Override
    public void onEvent(OrderBookUpdateEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.getOrderBook() == null) return;

        Map<Exchange, OrderBook> exchangeOrders;
        CurrencyPair pair = event.getCurrencyPair();
        if (orders.containsKey(pair))
            exchangeOrders = orders.get(pair);
        else {
            exchangeOrders = new HashMap<>();
            orders.put(pair, exchangeOrders);
        }
        OrderBook orderBook = event.getOrderBook();
        log.debug("Got {} asks and {} bids from {}", orderBook.getAsks().size(), orderBook.getBids().size(), event.getExchange());
        exchangeOrders.put(event.getExchange(), orderBook);
        updateLimitOrders();
    }

    private void updateLimitOrders() {
        for (Map.Entry<CurrencyPair, Map<Exchange, OrderBook>> e : orders.entrySet()) {
            Map<Exchange, OrderBook> orderBooks = e.getValue();
            if (orderBooks.size() < 2) return;

            CurrencyPair pair = e.getKey();
            updateLimitOrders(pair, orderBooks, Order.OrderType.ASK);
            updateLimitOrders(e.getKey(), e.getValue(), Order.OrderType.BID);
        }
    }

    BigDecimal limit = new BigDecimal("1000");
    static MathContext c = new MathContext(8, RoundingMode.HALF_UP);

    private void updateLimitOrders(CurrencyPair pair, Map<Exchange, OrderBook> orderBooks, Order.OrderType type) {
        //podsumuj wszystkie oferty do
        // - limitu stanu konta
        // - limitu różnicy ceny

        Map<Exchange, BigDecimal> values = new HashMap<>();
        Map<Exchange, BigDecimal> amounts = new HashMap<>();

        for (Map.Entry<Exchange, OrderBook> e : orderBooks.entrySet()) {
            BigDecimal valueSum = BigDecimal.ZERO;
            BigDecimal amountSum = BigDecimal.ZERO;
            for (LimitOrder order : get(e.getValue(), type)) {
                log.info("{}", order);
                BigDecimal amount = order.getTradableAmount();
                BigDecimal price = order.getLimitPrice();
                BigDecimal value = price.multiply(amount);
                BigDecimal diff = limit.subtract(valueSum);
                if (diff.compareTo(value) < 0) {
                    valueSum = valueSum.add(diff);
                    amountSum = amountSum.add(diff.divide(price, c));
                    break;
                } else {
                    valueSum = valueSum.add(value);
                    amountSum = amountSum.add(amount);
                }
            }
            values.put(e.getKey(), valueSum);
            amounts.put(e.getKey(), amountSum);

            log.info("{} {} value: {}\tamount: {} at price {}", type, pair, valueSum, amountSum, valueSum.divide(amountSum, c));
        }
    }

    private static final Logger log = LoggerFactory.getLogger(Dealer.class);
}
