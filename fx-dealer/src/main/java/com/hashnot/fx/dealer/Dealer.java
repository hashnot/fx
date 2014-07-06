package com.hashnot.fx.dealer;

import com.hashnot.fx.FeeHelper;
import com.hashnot.fx.framework.CacheUpdateEvent;
import com.hashnot.fx.framework.OrderUpdateEvent;
import com.hashnot.fx.framework.Simulation;
import com.hashnot.fx.spi.ExchangeCache;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.hashnot.fx.util.OrderBooks.get;

/**
 * @author Rafał Krupiński
 */
public class Dealer implements Runnable {

    private final Map<Exchange, ExchangeCache> context;
    private volatile boolean cont = true;

    public Simulation simulation;

    public Dealer(Map<Exchange, ExchangeCache> context, Simulation simulation, BlockingQueue<CacheUpdateEvent> inQueue, BlockingQueue<OrderUpdateEvent> outQueue) {
        this.context = context;
        this.simulation = simulation;
        this.inQueue = inQueue;
        this.outQueue = outQueue;
    }

    final private BlockingQueue<CacheUpdateEvent> inQueue;
    final private BlockingQueue<OrderUpdateEvent> outQueue;

    final private ArrayList<CacheUpdateEvent> localQueue = new ArrayList<>(2 << 8);
    final private Collection<CurrencyPair> dirtyPairs = new HashSet<>();
    final private Collection<Exchange> dirtyExchanges = new HashSet<>();

    @Override
    public void run() {
        try {
            while (cont) {
                CacheUpdateEvent evt = inQueue.poll(100, TimeUnit.MILLISECONDS);
                if (evt == null) continue;

                inQueue.add(evt);
                inQueue.drainTo(localQueue);
                process();
                clear();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted", e);
        }
    }

    protected void clear() {
        localQueue.clear();
        dirtyPairs.clear();
        dirtyExchanges.clear();
    }

    protected void process() {
        for (CacheUpdateEvent evt : localQueue)
            dirtyPairs.addAll(evt.pairs);

        for (CurrencyPair pair : dirtyPairs) {
            for (Map.Entry<Exchange, ExchangeCache> e : context.entrySet()) {
                if (e.getValue().orderBooks.containsKey(pair))
                    dirtyExchanges.add(e.getKey());
            }
        }
        if (dirtyExchanges.size() < 2)
            return;

        for (CurrencyPair pair : dirtyPairs) {
            updateLimitOrders(pair, dirtyExchanges, Order.OrderType.ASK);
            updateLimitOrders(pair, dirtyExchanges, Order.OrderType.BID);
        }
    }

    private SortedMap<LimitOrder, Exchange> getBestOffers(CurrencyPair pair, Collection<Exchange> exchanges, Order.OrderType dir) {
        SortedMap<LimitOrder, Exchange> result = new TreeMap<>();
        for (Exchange x : exchanges) {
            List<LimitOrder> orders = get(context.get(x).orderBooks.get(pair), dir);
            if (!orders.isEmpty())
                result.put(orders.get(0), x);
        }
        return result;
    }

    private void clearOrders(CurrencyPair pair) {
        log.debug("TODO clearOrders");
    }

    private void updateLimitOrders(CurrencyPair pair, Collection<Exchange> exchanges, Order.OrderType type) {
        //podsumuj wszystkie oferty do
        // - limitu stanu konta
        // - limitu różnicy ceny

        SortedMap<LimitOrder, Exchange> bestOrders = getBestOffers(pair, exchanges, type);
        if (bestOrders.size() < 2) {
            clearOrders(pair);
            return;
        }

        List<Map.Entry<LimitOrder, Exchange>> bestOrdersList = new ArrayList<>(bestOrders.entrySet());
        for (int i = 0; i < bestOrdersList.size(); i++) {
            Map.Entry<LimitOrder, Exchange> best = bestOrdersList.get(i);
            Exchange bestExchange = best.getValue();
            List<LimitOrder> bestXOrders = get(context.get(bestExchange).orderBooks.get(pair), type);
            for (int j = i + 1; j < bestOrdersList.size(); j++) {
                Map.Entry<LimitOrder, Exchange> worse = bestOrdersList.get(j);
                LimitOrder worseOrder = worse.getKey();
                log.info("worse: {} @{}", worseOrder, worse.getValue());

                BigDecimal amount = BigDecimal.ZERO;
                BigDecimal dealAmount = BigDecimal.ZERO;
                BigDecimal openPrice = null;

                Exchange worseExchange = worse.getValue();

                for (LimitOrder order : bestXOrders) {
                    log.info("best: {} @{}", order, bestExchange);
                    if (compareOrders(worseOrder, order, worseExchange, bestExchange) >= 0) {
                        amount = amount.add(order.getTradableAmount());
                        if (amount.compareTo(worseOrder.getTradableAmount()) > 0) break;
                        dealAmount = dealAmount.add(worseOrder.getTradableAmount());
                        openPrice = order.getLimitPrice();
                        log.info("better: {} @{}", order, bestExchange);
                        break;
                    } else
                        break;
                }

                if (dealAmount != BigDecimal.ZERO) {
                    //TODO change limit price
                    LimitOrder myOrder = new LimitOrder(worseOrder.getType(), dealAmount, worseOrder.getCurrencyPair(), null, null, worseOrder.getLimitPrice());
                    LimitOrder open = new LimitOrder(worseOrder.getType(), dealAmount, worseOrder.getCurrencyPair(), null, null, openPrice);
                    simulation.pair(open, bestExchange, myOrder, worseExchange);
                }
            }
        }
    }

    private int compareOrders(LimitOrder o1, LimitOrder o2, Exchange e1, Exchange e2) {
        //if(true) return o1.compareTo(o2);
        BigDecimal feePercent = context.get(e1).feeService.getFeePercent(o1.getCurrencyPair());
        feePercent = feePercent.add(context.get(e2).feeService.getFeePercent(o2.getCurrencyPair()));

        BigDecimal p1 = o1.getLimitPrice();
        BigDecimal p2 = o2.getLimitPrice();
        int cmp = p1.compareTo(p2);


        if (cmp <= 0)
            p1 = FeeHelper.addPercent(p1, feePercent);
        else
            p2 = FeeHelper.addPercent(p2, feePercent);

        int result = p1.compareTo(p2) * (o1.getType() == Order.OrderType.BID ? -1 : 1);
        log.debug("{} <=> {} --> {} <=> {} = {}", o1.getLimitPrice(), o2.getLimitPrice(), p1, p2, result);
        return result;
    }

    private static final Logger log = LoggerFactory.getLogger(Dealer.class);
}
