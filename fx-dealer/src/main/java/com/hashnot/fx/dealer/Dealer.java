package com.hashnot.fx.dealer;

import com.hashnot.fx.framework.Simulation;
import com.hashnot.fx.spi.OrderBookUpdateEvent;
import com.hashnot.fx.util.CurrencyPairUtil;
import com.lmax.disruptor.EventHandler;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

import static com.hashnot.fx.util.CurrencyPairUtil.get;
import static com.hashnot.fx.util.CurrencyPairUtil.reverse;

/**
 * @author Rafał Krupiński
 */
public class Dealer implements EventHandler<OrderBookUpdateEvent> {
    private Map<CurrencyPair, Map<Exchange, OrderBook>> currencies = new HashMap<>();

    public Simulation simulation = new Simulation();

    private static SortedMap<LimitOrder, Exchange> getBestOffers(Map<Exchange, OrderBook> books, Order.OrderType dir) {
        SortedMap<LimitOrder, Exchange> result = new TreeMap<>();
        for (Map.Entry<Exchange, OrderBook> e : books.entrySet()) {
            List<LimitOrder> limitOrders = get(e.getValue(), dir);
            if (limitOrders.isEmpty()) continue;
            result.put(limitOrders.get(0), e.getKey());
        }
        return result;
    }

    @Override
    public void onEvent(OrderBookUpdateEvent event, long sequence, boolean endOfBatch) throws Exception {
        if (event.getOrderBook() == null) return;

        Map<Exchange, OrderBook> exchangeOrders;
        CurrencyPair pair = event.getCurrencyPair();
        if (currencies.containsKey(pair))
            exchangeOrders = currencies.get(pair);
        else {
            exchangeOrders = new HashMap<>();
            currencies.put(pair, exchangeOrders);
        }

        OrderBook orderBook = event.getOrderBook();
        log.debug("Got {} asks and {} bids from {}", orderBook.getAsks().size(), orderBook.getBids().size(), event.getExchange());
        exchangeOrders.put(event.getExchange(), orderBook);
        updateLimitOrders(Arrays.asList(pair));
    }

    private void updateLimitOrders(List<CurrencyPair> currencyPairs) {
        for (CurrencyPair pair : currencyPairs) {
            Map<Exchange, OrderBook> orderBooks = this.currencies.get(pair);
            if (orderBooks.size() < 2) return;

            updateLimitOrders(pair, orderBooks, Order.OrderType.ASK);
            updateLimitOrders(pair, orderBooks, Order.OrderType.BID);
        }
    }

    private void clearOrders() {
        log.debug("TODO clearOrders");
    }

    private void updateLimitOrders(CurrencyPair pair, Map<Exchange, OrderBook> orderBooks, Order.OrderType type) {
        //podsumuj wszystkie oferty do
        // - limitu stanu konta
        // - limitu różnicy ceny

        SortedMap<LimitOrder, Exchange> bestOrders = getBestOffers(orderBooks, type);
        if (bestOrders.size() < 2) {
            clearOrders();
            return;
        }

        List<Map.Entry<LimitOrder, Exchange>> bestOrdersList = new ArrayList<>(bestOrders.entrySet());
        for (int i = 0; i < bestOrdersList.size(); i++) {
            Map.Entry<LimitOrder, Exchange> best = bestOrdersList.get(i);
            Exchange bestExchange = best.getValue();
            List<LimitOrder> bestXOrders = get(orderBooks.get(bestExchange), type);
            for (int j = i + 1; j < bestOrdersList.size(); j++) {
                Map.Entry<LimitOrder, Exchange> worse = bestOrdersList.get(j);
                LimitOrder worseOrder = worse.getKey();
                log.info("worse: {}", worseOrder);

                BigDecimal amount = BigDecimal.ZERO;

                for (LimitOrder order : bestXOrders) {
                    if (order.compareTo(worseOrder) < 0) {
                        amount = amount.add(order.getTradableAmount());
                        if (amount.compareTo(worseOrder.getTradableAmount()) > 0) break;
                        log.info("better: {}", order, order.getLimitPrice().subtract(worseOrder.getLimitPrice()));
                        simulation.add((order), bestExchange);
                    } else
                        break;
                }

                simulation.add(reverse(worseOrder), worse.getValue());
            }
        }
    }

    private static final Logger log = LoggerFactory.getLogger(Dealer.class);
}
