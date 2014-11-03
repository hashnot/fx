package com.hashnot.fx.ext.impl;

import com.hashnot.fx.ext.ITradesListener;
import com.hashnot.fx.ext.ITradesMonitor;
import com.hashnot.fx.spi.ILimitOrderPlacementListener;
import com.hashnot.fx.ext.ITradeListener;
import com.hashnot.fx.spi.ext.ITradeMonitor;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.LimitOrder;
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
public class TrackingTradesMonitor implements ITradesListener, ILimitOrderPlacementListener, ITradeMonitor {
    final private static Logger log = LoggerFactory.getLogger(TrackingTradesMonitor.class);

    final private Set<ITradeListener> tradeListeners = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ITradesMonitor tradesMonitor;


    //state

    final private Map<String, OrderPair> orders = new HashMap<>();
    private boolean running = false;

    // volatile state, during single call
    final private Map<OrderPair, Trade> changed = new HashMap<>();

    @Override
    public void trades(Trades trades) {
        try {

            for (Trade trade : trades.getTrades()) {
                String id = trade.getOrderId();
                OrderPair pair = orders.get(id);
                if (pair == null) {
                    log.warn("Trade on an unknown order {}", trade.getOrderId());
                    continue;
                }

                BigDecimal currentAmount = pair.current.getTradableAmount().subtract(trade.getTradableAmount());
                int amountCmp = currentAmount.compareTo(ZERO);
                if (amountCmp <= 0) {
                    orders.remove(id);
                    pair.current = null;

                    if (amountCmp < 0)
                        log.warn("Calculated Order has negative amount {}", id);
                } else if (amountCmp > 0) {
                    pair.current = from(pair.current).tradableAmount(currentAmount).build();
                }

                changed.put(pair, trade);
            }

            for (Map.Entry<OrderPair, Trade> e : changed.entrySet()) {
                OrderPair pair = e.getKey();
                Trade trade = e.getValue();
                for (ITradeListener listener : tradeListeners) {
                    try {
                        listener.trade(pair.original, trade, pair.current);
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
        orders.put(id, new OrderPair(order, order));
        updateRunning();
    }

    public TrackingTradesMonitor(ITradesMonitor tradesMonitor) {
        this.tradesMonitor = tradesMonitor;
    }

    @Override
    public void addTradeListener(ITradeListener listener) {
        tradeListeners.add(listener);
        updateRunning();
    }

    @Override
    public void removeTradeListener(ITradeListener listener) {
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

