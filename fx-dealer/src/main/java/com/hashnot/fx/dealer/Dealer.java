package com.hashnot.fx.dealer;

import com.hashnot.fx.FeeHelper;
import com.hashnot.fx.IFeeService;
import com.hashnot.fx.framework.Simulation;
import com.hashnot.fx.spi.OrderBookUpdateEvent;
import com.lmax.disruptor.EventHandler;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import static com.hashnot.fx.util.CurrencyPairUtil.get;

/**
 * @author Rafał Krupiński
 */
public class Dealer implements EventHandler<OrderBookUpdateEvent> {
    private Map<CurrencyPair, Map<Exchange, OrderBook>> currencies = new HashMap<>();
    private Map<Exchange, IFeeService> feeServices;

    public Simulation simulation;

    public Dealer(Map<Exchange, IFeeService> feeServices, Simulation simulation) {
        this.feeServices = feeServices;
        this.simulation = simulation;
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

    private void updateLimitOrders(List<CurrencyPair> currencyPairs) throws IOException {
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

    private void updateLimitOrders(CurrencyPair pair, Map<Exchange, OrderBook> orderBooks, Order.OrderType type) throws IOException {
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
                log.info("worse: {} @{}", worseOrder, worse.getValue().getExchangeSpecification().getExchangeName());

                BigDecimal amount = BigDecimal.ZERO;
                BigDecimal dealAmount = BigDecimal.ZERO;
                BigDecimal openPrice = null;

                Exchange worseExchange = worse.getValue();

                for (LimitOrder order : bestXOrders) {
                    log.info("best: {} @{}", order, bestExchange.getExchangeSpecification().getExchangeName());
                    if (compareOrders(worseOrder, order, worseExchange, bestExchange) >= 0) {
                        amount = amount.add(order.getTradableAmount());
                        if (amount.compareTo(worseOrder.getTradableAmount()) > 0) break;
                        dealAmount = dealAmount.add(worseOrder.getTradableAmount());
                        openPrice = order.getLimitPrice();
                        log.info("better: {} @{}", order, bestExchange.getExchangeSpecification().getExchangeName());
                        break;
/*
                        simulation.open(order, bestExchange);
*/
                    } else
                        break;
                }

                if (dealAmount != BigDecimal.ZERO) {
                    //TODO change limit price
                    LimitOrder myOrder = new LimitOrder(worseOrder.getType(), dealAmount, worseOrder.getCurrencyPair(), null, null, worseOrder.getLimitPrice());
//                    simulation.close(myOrder, worseExchange);
                    LimitOrder open = new LimitOrder(worseOrder.getType(), dealAmount, worseOrder.getCurrencyPair(), null, null, openPrice);
                    simulation.pair(open, bestExchange, myOrder, worseExchange);
                }
            }
        }
    }

    private int compareOrders(LimitOrder o1, LimitOrder o2, Exchange e1, Exchange e2) throws IOException {
        //if(true) return o1.compareTo(o2);
        BigDecimal feePercent = feeServices.get(e1).getFeePercent(o1.getCurrencyPair());
        feePercent = feePercent.add(feeServices.get(e2).getFeePercent(o2.getCurrencyPair()));

        BigDecimal p1 = o1.getLimitPrice();
        BigDecimal p2 = o2.getLimitPrice();
        int cmp = p1.compareTo(p2);


        if (cmp <= 0)
            p1 = FeeHelper.addPercent(p1, feePercent);
        else
            p2 = FeeHelper.addPercent(p2, feePercent);

        return p1.compareTo(p2) * (o1.getType() == Order.OrderType.BID ? -1 : 1);
    }

    private static final Logger log = LoggerFactory.getLogger(Dealer.class);
}
