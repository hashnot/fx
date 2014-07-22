package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ConnectionException;
import com.hashnot.fx.util.Orders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.xeiam.xchange.dto.Order.OrderType;

/**
 * @author Rafał Krupiński
 */
public class NoopOrderUpdater implements IOrderUpdater {
    final private static Logger log = LoggerFactory.getLogger(NoopOrderUpdater.class);

    private final Map<OrderType, OrderUpdateEvent> openOrders;

    public NoopOrderUpdater(HashMap<OrderType, OrderUpdateEvent> openOrders) {
        this.openOrders = openOrders;
    }

    @Override
    public void update(OrderUpdateEvent evt) {
        try {
            OrderType key = evt.openedOrder.getType();
            OrderUpdateEvent self = openOrders.get(key);

            if (self == null) {
                open(evt);
            } else if (evt.openExchange.equals(self.openExchange)) {
                if (!Orders.equals(evt.openedOrder, self.openedOrder))
                     updateOrder(evt, self);
            } else {
                cancel(self);
                open(evt);
            }
            openOrders.put(key, evt);
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    private void open(OrderUpdateEvent update) throws IOException {
        log.info("Open @{} {} ", update.openExchange, update.openedOrder);
    }

    private void cancel(OrderUpdateEvent self) throws IOException {
        log.info("Cancel @{} {}", self.openExchange, self.openedOrder);
    }

    private void updateOrder(OrderUpdateEvent self, OrderUpdateEvent event) throws ConnectionException, IOException {
        log.info("U.Cancel @{} {}", self.openExchange, self.openedOrder);
        log.info("U.Open @{} {}", event.openExchange, event.openedOrder);
    }

}
