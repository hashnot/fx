package com.hashnot.xchange.event.trade.impl;

import com.google.common.collect.Sets;
import com.hashnot.xchange.event.trade.*;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;
import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.ZERO;

/**
 * Tracker is enabled whenever limit order is placed, and it's disabled when there are no known open orders.
 * Adding or removing {@link }UserTradeListener}s doesn't influence monitoring, unlike in case of other monitors.
 *
 * @author Rafał Krupiński
 */
public class OrderTracker implements IUserTradesListener, IOrderPlacementListener, IOrderTracker {
    final private static Logger log = LoggerFactory.getLogger(OrderTracker.class);

    final private Set<IUserTradeListener> listeners = Sets.newConcurrentHashSet();

    final private IUserTradesMonitor userTradesMonitor;

    final private Map<String, LimitOrder> monitored = new HashMap<>();
    final private Map<String, LimitOrder> monitoredView = Collections.unmodifiableMap(monitored);

    final private Map<String, LimitOrder> current = new HashMap<>();
    final private Map<String, LimitOrder> currentView = Collections.unmodifiableMap(current);

    @Override
    public void trades(UserTradesEvent evt) {
        Exchange exchange = evt.source;
        if (monitored.isEmpty()) {
            log.warn("Received user trades from exchange {} where no orders are registered", exchange);
            return;
        }

        // TODO merge trades with the same price

        for (UserTrade trade : evt.userTrades.getUserTrades()) {
            String orderId = trade.getOrderId();
            LimitOrder monitoredOrder = monitored.get(orderId);
            if (monitoredOrder == null) {
                log.warn("Trade on an unknown order {}", trade);
                continue;
            }

            LimitOrder currentOrder = current.get(orderId);

            BigDecimal currentAmount = currentOrder.getTradableAmount().subtract(trade.getTradableAmount());
            int amountCmp = currentAmount.compareTo(ZERO);
            if (amountCmp <= 0) {
                monitored.remove(orderId);
                current.remove(orderId);
                currentOrder = null;

                if (amountCmp < 0)
                    log.warn("Calculated Order has negative amount {}", orderId);
            } else {
                currentOrder = from(monitoredOrder).tradableAmount(currentAmount).build();
                current.put(orderId, currentOrder);
            }

            multiplex(listeners, new UserTradeEvent(monitoredOrder, trade, currentOrder, exchange), IUserTradeListener::trade);
        }

        updateRunning();
    }

    private void updateRunning() {
        // listen only if we have registered orders
        if (monitored.isEmpty())
            userTradesMonitor.removeTradesListener(this);
        else
            userTradesMonitor.addTradesListener(this);
    }

    @Override
    public void limitOrderPlaced(OrderEvent<LimitOrder> evt) {
        String id = evt.id;

        // add ID
        LimitOrder limitOrder = from(evt.order).id(id).build();

        LimitOrder old = monitored.put(id, limitOrder);
        if (old != null) {
            log.warn("Order {} already monitored", id);
            return;
        } else
            log.info("Tracking {}", id);

        current.put(id, limitOrder);
        updateRunning();
    }

    @Override
    public void marketOrderPlaced(OrderEvent<MarketOrder> orderEvent) {
        // nothing to do, only limit orders can be tracked
    }

    @Override
    public void orderCanceled(OrderCancelEvent evt) {
        Exchange x = evt.source;
        String id = evt.id;

        LimitOrder limitOrder = current.get(id);
        if (limitOrder == null)
            log.warn("Cancelled unknown order {}@{}", id, x);
        else {
            log.info("Removing {}@{}", id, x);
            monitored.remove(id);
            current.remove(id);
            updateRunning();
        }
    }

    public OrderTracker(IUserTradesMonitor userTradesMonitor) {
        this.userTradesMonitor = userTradesMonitor;
    }

    @Override
    public Map<String, LimitOrder> getMonitored() {
        return monitoredView;
    }

    @Override
    public Map<String, LimitOrder> getCurrent() {
        return currentView;
    }

    @Override
    public void addTradeListener(IUserTradeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeTradeListener(IUserTradeListener listener) {
        listeners.remove(listener);
    }
}

