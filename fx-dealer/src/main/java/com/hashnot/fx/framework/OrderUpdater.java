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

    @Override
    public void update(OrderUpdateEvent evt) {
        try {
            if (evt.clear != null) {
                OrderUpdateEvent self = openOrders.get(evt.clear);
                if (self != null) {
                    cancel(self);
                    openOrders.remove(evt.clear);
                }
                return;
            }
            OrderType key = evt.openedOrder.getType();
            OrderUpdateEvent self = openOrders.get(key);

            if (self == null) {
                open(evt);
            } else if (evt.openExchange.equals(self.openExchange)) {
                if (!Orders.equals(evt.openedOrder, self.openedOrder))
                    updateOrder(self, evt);
            } else {
                cancel(self);
                open(evt);
            }
            openOrders.put(key, evt);
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    protected void open(OrderUpdateEvent update) throws IOException {
        log.info("Open @{} {} ", update.openExchange, update.openedOrder);
        update.openOrderId = update.openExchange.getPollingTradeService().placeLimitOrder(update.openedOrder);
    }

    protected void cancel(OrderUpdateEvent self) throws IOException {
        log.info("Cancel @{} {} ", self.openExchange, self.openedOrder);
        self.openExchange.getPollingTradeService().cancelOrder(self.openOrderId);
    }

    protected void updateOrder(OrderUpdateEvent self, OrderUpdateEvent event) throws ConnectionException, IOException {
        cancel(self);
        open(event);
    }

}
