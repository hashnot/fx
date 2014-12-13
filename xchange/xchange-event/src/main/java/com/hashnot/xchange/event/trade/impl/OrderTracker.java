package com.hashnot.xchange.event.trade.impl;

import com.google.common.collect.Sets;
import com.hashnot.xchange.event.trade.*;
import com.hashnot.xchange.ext.trade.IOrderPlacementListener;
import com.hashnot.xchange.ext.trade.OrderCancelEvent;
import com.hashnot.xchange.ext.trade.OrderEvent;
import com.hashnot.xchange.ext.util.Numbers;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.Order.OrderType;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;
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

    final private Map<String, LimitOrder> monitored = new ConcurrentHashMap<>();
    final private Map<String, LimitOrder> monitoredView = Collections.unmodifiableMap(monitored);

    final private Map<String, LimitOrder> current = new ConcurrentHashMap<>();
    final private Map<String, LimitOrder> currentView = Collections.unmodifiableMap(current);

    final private Map<OrderType, MarketOrder> marketOrders = Collections.synchronizedMap(new EnumMap<>(OrderType.class));

    final private Set<UserTrade> ignored = Sets.newConcurrentHashSet();

    @Override
    public void trades(UserTradesEvent evt) {
        Exchange exchange = evt.source;
        if (monitored.isEmpty() && marketOrders.isEmpty()) {
            log.warn("Received user trades from exchange {} where no orders are registered", exchange);
            return;
        }

        List<UserTrade> userTrades = evt.userTrades.getUserTrades();
        for (UserTrade trade : userTrades)
            handleTrade(exchange, trade);

        updateRunning();
    }

    protected void handleTrade(Exchange exchange, UserTrade trade) {
        if (marketOrders.containsKey(trade.getType())) {
            handleMarketOrderTrade(exchange, trade);
            return;
        }

        String orderId = trade.getOrderId();
        LimitOrder monitoredOrder = monitored.get(orderId);
        if (monitoredOrder == null) {
            if (ignored.add(trade))
                log.warn("Trade on an unknown order {}", trade);
            return;
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
            currentOrder = LimitOrder.Builder.from(monitoredOrder).tradableAmount(currentAmount).build();
            current.put(orderId, currentOrder);
        }

        multiplex(listeners, new UserTradeEvent(monitoredOrder, trade, currentOrder, exchange), IUserTradeListener::trade);
    }

    private void handleMarketOrderTrade(Exchange exchange, UserTrade trade) {
        MarketOrder prev;
        MarketOrder result;
        synchronized (marketOrders) {
            prev = marketOrders.get(trade.getType());
            if (prev == null) {
                log.warn("Could not find market order {}@{}", trade.getType(), exchange);
                return;
            }

            result = marketOrders.compute(trade.getType(), (k, v) -> {
                if (v == null) {
                    return null;
                } else {
                    BigDecimal remain = v.getTradableAmount().subtract(trade.getTradableAmount());
                    if (Numbers.BigDecimal.isZero(remain))
                        return null;
                    else
                        return MarketOrder.Builder.from(v).tradableAmount(remain).build();
                }
            });
        }

        multiplex(listeners, new UserTradeEvent(prev, trade, result, exchange), IUserTradeListener::trade);
    }

    private void updateRunning() {
        // listen only if we have registered orders
        if (monitored.isEmpty() && marketOrders.isEmpty())
            userTradesMonitor.removeTradesListener(this);
        else
            userTradesMonitor.addTradesListener(this);
    }

    @Override
    public void limitOrderPlaced(OrderEvent<LimitOrder> evt) {
        String id = evt.id;
        LimitOrder order = evt.order;

        // handle BTC-e market order work-around
        if ("0".equals(id)) {
            log.debug("BTC-e market order ID workaround");
            marketOrderPlaced(new OrderEvent<>(id, MarketOrder.Builder.from(order).build(), evt.source));
            return;
        }

        // add ID
        LimitOrder limitOrder = LimitOrder.Builder.from(order).id(id).build();

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
        MarketOrder order = orderEvent.order;
        marketOrders.compute(order.getType(), (k, v) -> {
            if (v == null)
                return order;
            else
                return MarketOrder.Builder.from(v).tradableAmount(v.getTradableAmount().add(order.getTradableAmount())).build();
        });
        updateRunning();
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

