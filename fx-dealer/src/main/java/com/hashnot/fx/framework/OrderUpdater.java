package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ConnectionException;
import com.hashnot.fx.util.Orders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static com.xeiam.xchange.dto.Order.OrderType;

/**
 * @author Rafał Krupiński
 */
public class OrderUpdater implements IOrderUpdater {
    final private static Logger log = LoggerFactory.getLogger(OrderUpdater.class);
    final private Map<OrderType, OrderUpdateEvent> openOrders;

    public OrderUpdater(Map<OrderType, OrderUpdateEvent> openOrders) {
        this.openOrders = openOrders;
    }

    /*
     old value is called self, new - evt
     */
    @Override
    public void update(OrderUpdateEvent evt) {
        try {
            if (evt.clear != null) {
                OrderUpdateEvent self = openOrders.get(evt.clear);
                if (self != null) {
                    cancel(evt.clear, self);
                }
                return;
            }
            OrderType key = evt.openedOrder.getType();
            OrderUpdateEvent self = openOrders.get(key);

            if (self == null) {
                open(key, evt);
            } else if (!evt.openExchange.equals(self.openExchange)) {
                updateOrder(self, evt, key);
            } else if (!Orders.equalsOrLess(evt.openedOrder, self.openedOrder)) {
                updateOrder(self, evt, key);
            }
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    protected void open(OrderType type, OrderUpdateEvent update) throws IOException {
        if(openOrders.containsKey(type))
            throw new IllegalStateException("Seems order already open " + type);
        log.info("Open @{} {} ", update.openExchange, update.openedOrder);
        update.openOrderId = update.openExchange.getPollingTradeService().placeLimitOrder(update.openedOrder);
        log.info("Opened {}", update.openOrderId);
        openOrders.put(type, update);
    }

    protected void cancel(OrderType key, OrderUpdateEvent self) throws IOException {
        log.info("Cancel @{} {} {}", self.openExchange, self.openOrderId, self.openedOrder);
        self.openExchange.getPollingTradeService().cancelOrder(self.openOrderId);
        self.openOrderId = null;
        openOrders.remove(key);

    }

    protected void updateOrder(OrderUpdateEvent self, OrderUpdateEvent event, OrderType type) throws ConnectionException, IOException {
        cancel(type, self);
        open(type, event);
    }

}
