package com.hashnot.fx.dealer;

import com.hashnot.fx.OrderBookUpdateEvent;
import com.hashnot.fx.ext.IOrderBookListener;
import com.hashnot.fx.framework.IOrderUpdater;
import com.hashnot.fx.framework.OrderUpdateEvent;
import com.hashnot.fx.framework.Simulation;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.util.OrderBooks;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

import static com.hashnot.fx.util.Numbers.lt;
import static com.hashnot.fx.util.Orders.*;

/**
 * @author Rafał Krupiński
 */
public class Dealer implements IOrderBookListener {
    private final Collection<IExchange> context;

    private final IOrderUpdater orderUpdater;

    public Simulation simulation;

    private final Map<OrderBookKey, OrderBook> orderBooks = new HashMap<>();

    public Dealer(Collection<IExchange> context, Simulation simulation, IOrderUpdater orderUpdater) {
        this.context = context;
        this.simulation = simulation;
        this.orderUpdater = orderUpdater;
    }

    final private Collection<IExchange> dirtyExchanges = new HashSet<>();

    @Override
    public synchronized void orderBookChanged(OrderBookUpdateEvent evt) {
        OrderBooks.updateNetPrices(evt.after, evt.source.getMarketMetadata(evt.pair).getOrderFeeFactor());
        orderBooks.put(new OrderBookKey(evt.source, evt.pair), evt.after);
        try {
            process(Arrays.asList(evt.pair));
        } catch (RuntimeException | Error e) {
            log.error("Error", e);
            throw e;
        } finally {
            clear();
        }
    }

    protected void clear() {
        dirtyExchanges.clear();
    }

    protected void process(Collection<CurrencyPair> dirtyPairs) {
        for (IExchange e : context) {
            for (CurrencyPair pair : dirtyPairs) {
                if (orderBooks.containsKey(new OrderBookKey(e, pair))) {
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

    private NavigableMap<LimitOrder, IExchange> getBestOffers(CurrencyPair pair, Collection<IExchange> exchanges, Order.OrderType dir) {
        NavigableMap<LimitOrder, IExchange> result = new TreeMap<>((o1, o2) -> o1.getNetPrice().compareTo(o2.getNetPrice()) * factor(o1.getType()));
        for (IExchange x : exchanges) {
            List<LimitOrder> orders = orderBooks.get(new OrderBookKey(x, pair)).getOrders(dir);
            if (!orders.isEmpty())
                result.put(orders.get(0), x);
        }
        return result;
    }

    private boolean hasMinimumMoney(IExchange x, LimitOrder order, Order.OrderType dir) {
        BigDecimal outAmount = x.getWallet(Orders.outgoingCurrency(order, dir));

        BigDecimal baseAmount;
        if (dir == Order.OrderType.ASK)
            baseAmount = outAmount;
        else
            baseAmount = outAmount.divide(order.getLimitPrice(), c);

        return !lt(baseAmount, x.getMarketMetadata(order.getCurrencyPair()).getAmountMinimum());
    }

    private void clearOrders(CurrencyPair pair) {
        log.debug("TODO clearOrders");
    }

    private void updateLimitOrders(CurrencyPair pair, Collection<IExchange> exchanges, Order.OrderType type) {
        //podsumuj wszystkie oferty do
        // - limitu stanu konta
        // - limitu różnicy ceny

        NavigableMap<LimitOrder, IExchange> bestOrders = getBestOffers(pair, exchanges, type);
        if (bestOrders.size() < 2) {
            clearOrders(pair);
            return;
        }

        Order.OrderType reverseType = revert(type);

        // find best - closing orders
        Map.Entry<LimitOrder, IExchange> best = null;
        for (Map.Entry<LimitOrder, IExchange> e : bestOrders.entrySet()) {
            if (hasMinimumMoney(e.getValue(), e.getKey(), reverseType)) {
                best = e;
                break;
            }
        }

        if (best == null) {
            log.info("No exchange has sufficient funds to close {}", type);
            return;
        }

        // find worst - opening orders
        Map.Entry<LimitOrder, IExchange> worst = null;
        for (Map.Entry<LimitOrder, IExchange> e : bestOrders.descendingMap().entrySet()) {
            if (hasMinimumMoney(e.getValue(), e.getKey(), type)) {
                worst = e;
                break;
            }
        }

        if (worst == null) {
            log.info("No exchange has sufficient funds to open {}", type);
            return;
        } else if (worst == best) {
            log.info("Didn't find 2 exchanges with sufficient funds ({})", type);
            return;
        }

        LimitOrder worstOrder = worst.getKey();
        updateNetPrice(worstOrder, bestOrders.get(worstOrder));

        IExchange worstExchange = worst.getValue();
        IExchange bestExchange = best.getValue();

        LimitOrder closingBest = closing(best.getKey(), bestExchange);
        if (!isProfitable(worstOrder, closingBest)) {
            orderUpdater.update(new OrderUpdateEvent(type));
            return;
        }

        List<LimitOrder> closeOrders = orderBooks.get(new OrderBookKey(bestExchange, pair)).getOrders(type);
        OrderUpdateEvent event = simulation.deal(worstOrder, worstExchange, closeOrders, bestExchange);
        if (event != null)
            orderUpdater.update(event);
        else
            orderUpdater.update(new OrderUpdateEvent(type));
    }

    private static final Logger log = LoggerFactory.getLogger(Dealer.class);
}

class OrderBookKey {
    private final IExchange exchange;
    private final CurrencyPair pair;

    public OrderBookKey(IExchange exchange, CurrencyPair pair) {
        this.exchange = exchange;
        this.pair = pair;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OrderBookKey that = (OrderBookKey) o;

        return exchange.equals(that.exchange) && pair.equals(that.pair);

    }

    @Override
    public int hashCode() {
        int result = exchange.hashCode();
        result = 31 * result + pair.hashCode();
        return result;
    }
}
