package com.hashnot.fx.framework.impl;

import com.hashnot.fx.framework.ILimitOrderPlacementListener;
import com.hashnot.fx.framework.IUserTradeListener;
import com.hashnot.fx.framework.IUserTradeMonitor;
import com.hashnot.fx.framework.UserTradeEvent;
import com.hashnot.xchange.event.IUserTradesListener;
import com.hashnot.xchange.event.IUserTradesMonitor;
import com.hashnot.xchange.event.UserTradesEvent;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class TrackingUserTradesMonitor implements IUserTradesListener, ILimitOrderPlacementListener, IUserTradeMonitor {
    final private static Logger log = LoggerFactory.getLogger(TrackingUserTradesMonitor.class);

    final private Set<IUserTradeListener> tradeListeners = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final IUserTradesMonitor tradesMonitor;


    //state

    final private Map<String, OrderPair> orders = new HashMap<>();
    private boolean running = false;

    // volatile state, during single call
    final private Map<OrderPair, UserTrade> changed = new HashMap<>();

    @Override
    public void trades(UserTradesEvent evt) {
        try {

            for (UserTrade trade : evt.userTrades.getUserTrades()) {
                String orderId = trade.getOrderId();
                OrderPair pair = orders.get(orderId);
                if (pair == null) {
                    log.warn("Trade on an unknown order {}", orderId);
                    continue;
                }

                BigDecimal currentAmount = pair.current.getTradableAmount().subtract(trade.getTradableAmount());
                int amountCmp = currentAmount.compareTo(ZERO);
                if (amountCmp <= 0) {
                    orders.remove(orderId);
                    pair.current = null;

                    if (amountCmp < 0)
                        log.warn("Calculated Order has negative amount {}", orderId);
                } else if (amountCmp > 0) {
                    pair.current = from(pair.current).tradableAmount(currentAmount).build();
                }

                changed.put(pair, trade);
            }

            for (Map.Entry<OrderPair, UserTrade> e : changed.entrySet()) {
                OrderPair pair = e.getKey();
                UserTrade trade = e.getValue();
                for (IUserTradeListener listener : tradeListeners) {
                    try {
                        listener.trade(new UserTradeEvent(pair.original, trade, pair.current, evt.source));
                    } catch (RuntimeException e1) {
                        log.warn("Error", e);
                    }
                }
            }

            updateRunning();
        } finally {
            changed.clear();
        }
    }

    private synchronized void updateRunning() {
        if (running) {
            if (orders.isEmpty() || tradeListeners.isEmpty()) {
                tradesMonitor.addTradesListener(this);
                running = false;
            }
        } else if (!orders.isEmpty() && !tradeListeners.isEmpty()) {
            tradesMonitor.removeTradesListener(this);
            running = true;
        }
    }

    @Override
    public void limitOrderPlaced(LimitOrder order, String id) {
        // add ID
        LimitOrder limitOrder = from(order).id(id).build();
        orders.put(id, new OrderPair(limitOrder, limitOrder));
        updateRunning();
    }

    public TrackingUserTradesMonitor(IUserTradesMonitor tradesMonitor) {
        this.tradesMonitor = tradesMonitor;
    }

    @Override
    public void addTradeListener(IUserTradeListener listener) {
        tradeListeners.add(listener);
        updateRunning();
    }

    @Override
    public void removeTradeListener(IUserTradeListener listener) {
        tradeListeners.remove(listener);
        updateRunning();
    }

    // not implementing equals/hashcode - use identity
    protected static class OrderPair {
        public final LimitOrder original;
        public LimitOrder current;

        public OrderPair(LimitOrder original, LimitOrder current) {
            this.original = original;
            this.current = current;
        }
    }
}

