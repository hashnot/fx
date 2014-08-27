package com.hashnot.fx.framework;

import com.hashnot.fx.spi.IOrderListener;
import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * timer task
 *
 * @author Rafał Krupiński
 */
public class TradeMonitor implements Runnable {

    final private static Logger log = LoggerFactory.getLogger(TradeMonitor.class);

    final private IExchange exchange;
    final private PollingTradeService pollingTradeService;
    final private IOrderListener orderClosedListener;
    final private Map<Order.OrderType, OrderUpdateEvent> openOrders;

    final private Map<Order.OrderType, LimitOrder> currentOrders = new HashMap<>();

    public TradeMonitor(IExchange exchange, IOrderListener orderClosedListener, Map<Order.OrderType, OrderUpdateEvent> openOrders) {
        this.exchange = exchange;
        this.pollingTradeService = exchange.getPollingTradeService();
        this.orderClosedListener = orderClosedListener;
        this.openOrders = openOrders;
    }

    @Override
    public void run() {
        try {
            List<LimitOrder> currentOrders = pollingTradeService.getOpenOrders().getOpenOrders();

            for (LimitOrder order : currentOrders) {
                OrderUpdateEvent orderUpdate = openOrders.get(order.getType());
                if (orderUpdate == null) throw new IllegalStateException("Unknown order");
                this.currentOrders.put(order.getType(), order);
            }

            for (Map.Entry<Order.OrderType, OrderUpdateEvent> e : openOrders.entrySet()) {
                if (!e.getValue().openExchange.equals(exchange)) {
                    continue;
                }

                LimitOrder current = this.currentOrders.get(e.getKey());
                // if closed or touched
                if (current == null || current.getTradableAmount().compareTo(e.getValue().openedOrder.getTradableAmount()) < 0)
                    orderClosedListener.trade(e.getValue().openedOrder, current);
            }
        } catch (IOException e) {
            log.error("Error", e);
        } finally {
            currentOrders.clear();
        }
    }
}
