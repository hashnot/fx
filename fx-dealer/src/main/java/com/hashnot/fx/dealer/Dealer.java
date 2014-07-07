package com.hashnot.fx.dealer;

import com.hashnot.fx.framework.ICacheUpdateListener;
import com.hashnot.fx.framework.OrderUpdateEvent;
import com.hashnot.fx.framework.Simulation;
import com.hashnot.fx.spi.ExchangeCache;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.BlockingQueue;

import static com.hashnot.fx.util.OrderBooks.get;

/**
 * @author Rafał Krupiński
 */
public class Dealer implements ICacheUpdateListener {

    private final Map<Exchange, ExchangeCache> context;

    public Simulation simulation;

    public Dealer(Map<Exchange, ExchangeCache> context, Simulation simulation, BlockingQueue<OrderUpdateEvent> outQueue) {
        this.context = context;
        this.simulation = simulation;
        this.outQueue = outQueue;
    }

    final private BlockingQueue<OrderUpdateEvent> outQueue;

    final private Collection<Exchange> dirtyExchanges = new HashSet<>();

    @Override
    public void cacheUpdated(Collection<CurrencyPair> pairs) {
        try {
            process(pairs);
            clear();
        } catch (RuntimeException | Error e) {
            log.error("Error", e);
            throw e;
        }
    }

    protected void clear() {
        dirtyExchanges.clear();
    }

    protected void process(Collection<CurrencyPair> dirtyPairs) {
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
        simulation.apply(outQueue);
    }

    private SortedMap<LimitOrder, Exchange> getBestOffers(CurrencyPair pair, Collection<Exchange> exchanges, Order.OrderType dir) {
        SortedMap<LimitOrder, Exchange> result = new TreeMap<>((o1, o2) -> o1.getNetPrice().compareTo(o2.getNetPrice()) * Orders.factor(o1.getType()));
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

        Map<LimitOrder, Exchange> bestOrders = getBestOffers(pair, exchanges, type);
        if (bestOrders.size() < 2) {
            clearOrders(pair);
            return;
        }

        List<Map.Entry<LimitOrder, Exchange>> bestOrdersList = new ArrayList<>(bestOrders.entrySet());
        for (int i = 0; i < bestOrdersList.size(); i++) {
            Map.Entry<LimitOrder, Exchange> best = bestOrdersList.get(i);
            Exchange bestExchange = best.getValue();
            ExchangeCache bestX = context.get(bestExchange);
            List<LimitOrder> bestXOrders = get(bestX.orderBooks.get(pair), type);
            for (int j = i + 1; j < bestOrdersList.size(); j++) {
                Map.Entry<LimitOrder, Exchange> worse = bestOrdersList.get(j);
                LimitOrder worseOrder = worse.getKey();
                log.info("worse: {} @{}", worseOrder, worse.getValue());

                BigDecimal amount = BigDecimal.ZERO;
                BigDecimal dealAmount = BigDecimal.ZERO;
                BigDecimal openPrice = null;

                Exchange worseExchange = worse.getValue();

                for (LimitOrder order : bestXOrders) {
                    //log.info("best: {} @{}", order, bestExchange);

                    BigDecimal openNetPrice = getNetPrice(order, bestX);
                    int cmp = worseOrder.getNetPrice().compareTo(openNetPrice) * Orders.factor(worseOrder.getType());
                    log.debug("{} <=> {} --> {} <=> {} = {}", worseOrder.getLimitPrice(), order.getLimitPrice(), worseOrder.getNetPrice(), openNetPrice, cmp);
                    if (cmp >= 0) {
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
                    myOrder.setNetPrice(getNetPrice(myOrder, context.get(worseExchange)));
                    LimitOrder close = new LimitOrder(Orders.revert(worseOrder.getType()), dealAmount, worseOrder.getCurrencyPair(), null, null, openPrice);
                    close.setNetPrice(getNetPrice(close, bestX));
                    simulation.add(close, bestExchange, myOrder, worseExchange);
                }
            }
        }
    }

    private static BigDecimal getNetPrice(LimitOrder order, ExchangeCache bestX) {
        return Orders.getNetPrice(order.getLimitPrice(), Orders.revert(order.getType()), bestX.feeService.getFeePercent(order.getCurrencyPair()));
    }

    private static final Logger log = LoggerFactory.getLogger(Dealer.class);
}
