package com.hashnot.fx.dealer;

import com.hashnot.fx.framework.ICacheUpdateListener;
import com.hashnot.fx.framework.IOrderUpdater;
import com.hashnot.fx.framework.OrderUpdateEvent;
import com.hashnot.fx.framework.Simulation;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.hashnot.fx.util.OrderBooks.get;
import static com.hashnot.fx.util.Orders.closing;
import static com.hashnot.fx.util.Orders.isProfitable;

/**
 * @author Rafał Krupiński
 */
public class Dealer implements ICacheUpdateListener {

    private final Collection<IExchange> context;

    private final IOrderUpdater orderUpdater;

    public Simulation simulation;

    public Dealer(Collection<IExchange> context, Simulation simulation, IOrderUpdater orderUpdater) {
        this.context = context;
        this.simulation = simulation;
        this.orderUpdater = orderUpdater;
    }

    final private Collection<IExchange> dirtyExchanges = new HashSet<>();

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
        for (IExchange e : context) {
            for (CurrencyPair pair : dirtyPairs) {
                if (e.getOrderBooks().containsKey(pair)) {
                    dirtyExchanges.add(e);
                    break;
                }
            }
        }
        if (dirtyExchanges.size() < 2)
            return;

        for (CurrencyPair pair : dirtyPairs) {
            updateLimitOrders(pair, dirtyExchanges, Order.OrderType.ASK);
            updateLimitOrders(pair, dirtyExchanges, Order.OrderType.BID);
        }
    }

    private SortedMap<LimitOrder, IExchange> getBestOffers(CurrencyPair pair, Collection<IExchange> exchanges, Order.OrderType dir) {
        SortedMap<LimitOrder, IExchange> result = new TreeMap<>((o1, o2) -> o1.getNetPrice().compareTo(o2.getNetPrice()) * Orders.factor(o1.getType()));
        for (IExchange x : exchanges) {
            List<LimitOrder> orders = get(x.getOrderBook(pair), dir);
            if (!orders.isEmpty())
                result.put(orders.get(0), x);
        }
        return result;
    }

    private void clearOrders(CurrencyPair pair) {
        log.debug("TODO clearOrders");
    }

    private void updateLimitOrders(CurrencyPair pair, Collection<IExchange> exchanges, Order.OrderType type) {
        //podsumuj wszystkie oferty do
        // - limitu stanu konta
        // - limitu różnicy ceny

        SortedMap<LimitOrder, IExchange> bestOrders = getBestOffers(pair, exchanges, type);
        if (bestOrders.size() < 2) {
            clearOrders(pair);
            return;
        }

        List<Map.Entry<LimitOrder, IExchange>> bestOrdersList = new ArrayList<>(bestOrders.entrySet());

        Map.Entry<LimitOrder, IExchange> best = bestOrdersList.get(0);
        LimitOrder worstOrder = bestOrders.lastKey();
        IExchange worstExchange = bestOrders.get(worstOrder);
        IExchange bestExchange = best.getValue();
        LimitOrder closingBest = closing(best.getKey(), bestExchange);
        if (!isProfitable(worstOrder, closingBest)) {
            orderUpdater.update(new OrderUpdateEvent(type));
            return;
        }

        List<LimitOrder> closeOrders = get(bestExchange.getOrderBook(pair), type);
        OrderUpdateEvent event = simulation.deal(worstOrder, worstExchange, closeOrders, bestExchange);
        if (event != null)
            orderUpdater.update(event);
    }

    private static final Logger log = LoggerFactory.getLogger(Dealer.class);
}
