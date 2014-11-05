package com.hashnot.fx.framework.impl;

import com.hashnot.fx.framework.ILimitOrderPlacementListener;
import com.hashnot.fx.framework.IOrderTracker;
import com.hashnot.fx.framework.IUserTradeListener;
import com.hashnot.fx.framework.UserTradeEvent;
import com.hashnot.xchange.event.IOpenOrdersListener;
import com.hashnot.xchange.event.OpenOrdersEvent;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;

/**
 * @author Rafał Krupiński
 */
public class OrderTracker implements IOrderTracker, ILimitOrderPlacementListener, IUserTradeListener, IOpenOrdersListener {
    final private static Logger log = LoggerFactory.getLogger(OrderTracker.class);

    private final Map<String, LimitOrder> orders = new HashMap<>();
    private final Map<String, LimitOrder> ordersView = Collections.unmodifiableMap(orders);

    private final Map<String, LimitOrder> current = new HashMap<>();
    private final Map<String, LimitOrder> currentView = Collections.unmodifiableMap(current);


    @Override
    public void limitOrderPlaced(LimitOrder order, String id) {
        // add ID
        LimitOrder limitOrder = from(order).id(id).build();
        orders.put(id, limitOrder);
        current.put(id, limitOrder);
    }

    @Override
    public void trade(UserTradeEvent evt) {
        String orderId = evt.monitored.getId();
        assert orderId != null;
        LimitOrder old = current.get(orderId);
        if (old == null) {
            log.warn("Trade on a not tracked order {}", orderId);
            return;
        }
        if (evt.current == null) {
            orders.remove(orderId);
            current.remove(orderId);
        }
    }

    // volatile openOrders data
    private final Set<String> sessionClosedOrders = new HashSet<>();

    @Override
    public void openOrders(OpenOrdersEvent evt) {
        sessionClosedOrders.addAll(orders.keySet());

        for (LimitOrder order : evt.openOrders.getOpenOrders())
            sessionClosedOrders.remove(order.getId());

        for (String orderId : sessionClosedOrders) {
            orders.remove(orderId);
            current.remove(orderId);
        }

        sessionClosedOrders.clear();
    }

    @Override
    public Map<String, LimitOrder> getMonitored() {
        return ordersView;
    }

    @Override
    public Map<String, LimitOrder> getCurrent() {
        return currentView;
    }
}
