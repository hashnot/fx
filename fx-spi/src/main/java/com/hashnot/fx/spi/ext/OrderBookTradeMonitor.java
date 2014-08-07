package com.hashnot.fx.spi.ext;

import com.hashnot.fx.OrderBookUpdateEvent;
import com.hashnot.fx.spi.IOrderBookListener;
import com.hashnot.fx.spi.IOrderListener;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.util.*;

/**
 * @author Rafał Krupiński
 */
public class OrderBookTradeMonitor implements IOrderBookListener {
    protected final Map<String, LimitOrder> openOrders;

    /* my part -> current */
    protected final Map<LimitOrder, LimitOrder> monitoredOrders = new HashMap<>();

    protected final List<IOrderListener> tradeListeners = new LinkedList<>();

    /* orders that are unmonitorable because I don't know if my order is first of its price,*/
    protected final Set<String> notMonitorable = new HashSet<>();

    public OrderBookTradeMonitor(Map<String, LimitOrder> openOrders) {
        this.openOrders = openOrders;
    }

    @Override
    public void changed(OrderBookUpdateEvent orderBookUpdateEvent) {
        LimitOrder monitored = null;
        LimitOrder current = null;
        for (IOrderListener tradeListener : tradeListeners) {
            tradeListener.trade(monitored, current);
        }

    }
}
