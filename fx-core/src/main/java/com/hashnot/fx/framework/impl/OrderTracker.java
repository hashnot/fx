package com.hashnot.fx.framework.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.fx.framework.*;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.IUserTradesListener;
import com.hashnot.xchange.event.IUserTradesMonitor;
import com.hashnot.xchange.event.UserTradesEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;
import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.ZERO;
import static java.util.Collections.emptyMap;

/**
 * @author Rafał Krupiński
 */
public class OrderTracker implements IUserTradesListener, ILimitOrderPlacementListener, IUserTradeMonitor, IOrderTracker {
    final private static Logger log = LoggerFactory.getLogger(OrderTracker.class);

    private final Multimap<Exchange, IUserTradeListener> listeners = Multimaps.newSetMultimap(new HashMap<>(), () -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

    final private Map<Exchange, IExchangeMonitor> monitors;

    final private Map<Exchange, Map<String, LimitOrder>> monitored = new HashMap<>();
    final private Map<Exchange, Map<String, LimitOrder>> monitoredView = Collections.unmodifiableMap(monitored);

    final private Map<Exchange, Map<String, LimitOrder>> current = new HashMap<>();
    final private Map<Exchange, Map<String, LimitOrder>> currentView = Collections.unmodifiableMap(current);

    @Override
    public void trades(UserTradesEvent evt) {
        Exchange exchange = evt.source;
        Map<String, LimitOrder> monitored = this.monitored.getOrDefault(exchange, emptyMap());
        if (monitored.isEmpty()) {
            log.warn("Received user trades from exchange {} where no orders are registered", exchange);
            return;
        }

        Map<String, LimitOrder> current = this.current.get(exchange);

        for (UserTrade trade : evt.userTrades.getUserTrades()) {
            String orderId = trade.getOrderId();
            LimitOrder monitoredOrder = monitored.get(orderId);
            if (current == null) {
                log.warn("Trade on an unknown order {}", orderId);
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

            multiplex(listeners.get(exchange), new UserTradeEvent(monitoredOrder, trade, currentOrder, exchange), (l, e) -> l.trade(e));
        }

        updateRunning(exchange);
    }

    private void updateRunning(Exchange exchange) {
        IUserTradesMonitor userTradesMonitor = monitors.get(exchange).getUserTradesMonitor();

        // listen only if we have registered orders
        if (monitored.getOrDefault(exchange, emptyMap()).isEmpty())
            userTradesMonitor.removeTradesListener(this);
        else
            userTradesMonitor.addTradesListener(this);
    }

    @Override
    public void limitOrderPlaced(OrderEvent evt) {
        String id = evt.id;

        // add ID
        LimitOrder limitOrder = from(evt.order).id(id).build();

        Exchange source = evt.source;
        Map<String, LimitOrder> monitored = this.monitored.computeIfAbsent(source, (k) -> new HashMap<>());
        LimitOrder old = monitored.put(id, limitOrder);
        if (old != null) {
            log.warn("Order {} already monitored", id);
            return;
        }

        Map<String, LimitOrder> current = this.current.computeIfAbsent(source, (k) -> new HashMap<>());
        current.put(id, limitOrder);
    }

    public OrderTracker(Map<Exchange, IExchangeMonitor> monitors) {
        this.monitors = monitors;
    }

    @Override
    public Map<String, LimitOrder> getMonitored(Exchange exchange) {
        return monitoredView.getOrDefault(exchange, emptyMap());
    }

    @Override
    public Map<String, LimitOrder> getCurrent(Exchange exchange) {
        return currentView.getOrDefault(exchange, emptyMap());
    }

    @Override
    public void addTradeListener(IUserTradeListener listener, Exchange exchange) {
        listeners.put(exchange, listener);
        monitors.get(exchange).getUserTradesMonitor().addTradesListener(this);
    }

    @Override
    public void removeTradeListener(IUserTradeListener listener, Exchange exchange) {
        listeners.remove(exchange, listener);
        if (!listeners.containsKey(exchange))
            monitors.get(exchange).getUserTradesMonitor().removeTradesListener(this);
    }
}

