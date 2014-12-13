package com.hashnot.fx.framework.impl;

import com.hashnot.fx.framework.IOrderBookSideListener;
import com.hashnot.fx.framework.OrderBookSideUpdateEvent;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.trade.IUserTradeListener;
import com.hashnot.xchange.event.trade.IUserTradeMonitor;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.hashnot.xchange.ext.trade.IOrderPlacementListener;
import com.hashnot.xchange.ext.trade.OrderCancelEvent;
import com.hashnot.xchange.ext.trade.OrderEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.hashnot.xchange.ext.util.Comparables.eq;

/**
 * Lifecycle of a monitored order
 * <p>
 * 1 opened by us -> openOrders
 * 2 received from exchange
 * <p>
 * 3 amount matches - monitored -> monitoredOrder, monitoredCurrent, monitoredRemaining
 * 4 received change
 * 5 if less than previous value - decrease current value
 * 6 if fully executed - remove
 * <p>
 * OR
 * <p>
 * 3 amount doesn't match - enable open order monitor
 * <p>
 * <p>
 * In theory we could want to keep two orders at single price, but in different directions.
 * This should be considered an error, because these transactions would be redundant,
 * and lossy because of the fees
 * <p>
 * TODO not fully implemented
 *
 * @author Rafał Krupiński
 */
public class OrderBookUserTradeMonitor implements IOrderBookSideListener, IOrderPlacementListener, IUserTradeMonitor {
    final private static Logger log = LoggerFactory.getLogger(OrderBookUserTradeMonitor.class);

    /**
     * price -> my open order, before the confirmation
     */
    protected final Map<BigDecimal, LimitOrder> openOrders = new HashMap<>();

    /**
     * price -> current order amount
     */
    protected final Map<BigDecimal, BigDecimal> monitoredCurrent = new ConcurrentHashMap<>();

    /**
     * price -> original order
     */
    protected final Map<BigDecimal, LimitOrder> monitoredOrder = new ConcurrentHashMap<>();

    protected final List<IUserTradeListener> tradeListeners = new LinkedList<>();

    /* orders that are not monitorable because we don't know if our order is first of all of its price,*/
    protected final Set<String> notMonitorable = new HashSet<>();


    protected final IAsyncTradeService tradeService;

    public OrderBookUserTradeMonitor(IAsyncTradeService tradeService) {
        this.tradeService = tradeService;
    }

    @Override
    public void limitOrderPlaced(OrderEvent<LimitOrder> evt) {
        openOrders.put(evt.order.getLimitPrice(), evt.order);
    }

    @Override
    public void marketOrderPlaced(OrderEvent<MarketOrder> orderEvent) {

    }

    @Override
    public void orderCanceled(OrderCancelEvent orderCancelEvent) {
        // TODO implement
    }

    @Override
    public void orderBookSideChanged(OrderBookSideUpdateEvent evt) {
        if (evt.newOrders.isEmpty()) {
            return;
        }

        if (tradeListeners.isEmpty()) {
            log.warn("Received order book update but no one is listening");
            return;
        }

        Iterator<LimitOrder> afterIter = evt.getChanges().iterator();
        Exchange source = evt.source.market.exchange;

        for (LimitOrder change : evt.newOrders) {
            BigDecimal price = change.getLimitPrice();

            LimitOrder afterOrder = find(afterIter, price);

            changed(afterOrder, price, source);
        }
    }

    private void changed(LimitOrder afterOrder, BigDecimal price, Exchange source) {
        LimitOrder monitored = monitoredOrder.get(price);

        if (monitored != null) {
            BigDecimal currentAmount = monitoredCurrent.get(price);
            if (afterOrder == null) {
                notify(monitored, null, source);
            } else {
                int amountCmp = afterOrder.getTradableAmount().compareTo(currentAmount);
                if (amountCmp > 0) {
                    addNotMonitorable(afterOrder);
                    monitoredOrder.remove(price);
                    monitoredCurrent.remove(price);
                } else if (amountCmp < 0) {
                    monitoredCurrent.put(price, afterOrder.getTradableAmount());
                    notify(monitored, afterOrder, source);
                } else {
                    log.warn("Illegal state: order after change is equal to the state before the change: {}", afterOrder);
                }
            }
        } else if (openOrders.containsKey(price)) {
            LimitOrder original = openOrders.remove(price);
            int amountCmp = original.getTradableAmount().compareTo(afterOrder.getTradableAmount());
            if (amountCmp > 0) {
                addNotMonitorable(afterOrder);
            } else {
                monitoredCurrent.put(price, afterOrder.getTradableAmount());
                monitoredOrder.put(price, original);

                if (amountCmp < 0)
                    notify(original, afterOrder, source);
            }
        }

    }

    private void notify(LimitOrder original, LimitOrder afterOrder, Exchange source) {
        BigDecimal amount = afterOrder != null ? afterOrder.getTradableAmount() : BigDecimal.ZERO;
        BigDecimal price = afterOrder != null ? afterOrder.getLimitPrice() : null;
        UserTrade trade = new UserTrade(original.getType(), original.getTradableAmount().subtract(amount), original.getCurrencyPair(), price, null, null, null, null, null);

        for (IUserTradeListener orderListener : tradeListeners)
            try {
                orderListener.trade(new UserTradeEvent(original, trade, afterOrder, source));
            } catch (Exception e) {
                log.warn("Error", e);
            }
    }


    private void addNotMonitorable(LimitOrder order) {
        notMonitorable.add(order.getId());
        // TODO support for not monitorable
        if (notMonitorable.size() == 1) ;
    }

    private LimitOrder find(Iterator<LimitOrder> iter, BigDecimal price) {
        while (iter.hasNext()) {
            LimitOrder next = iter.next();
            if (eq(next.getLimitPrice(), price))
                return next;
        }
        return null;
    }

    @Override
    public void addTradeListener(IUserTradeListener orderListener) {
        tradeListeners.add(orderListener);
    }

    @Override
    public void removeTradeListener(IUserTradeListener orderListener) {
        tradeListeners.remove(orderListener);
    }
}
