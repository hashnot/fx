package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import static com.xeiam.xchange.dto.Order.OrderType;

/**
 * @author Rafał Krupiński
 */
public class NoopOrderUpdater extends OrderUpdater {
    final private static Logger log = LoggerFactory.getLogger(NoopOrderUpdater.class);

    public NoopOrderUpdater(Map<OrderType, OrderUpdateEvent> openOrders) {
        super(openOrders);
    }

    protected void open(OrderType type, OrderUpdateEvent update) throws IOException {
        log.info("Open #{} {} ", update.openExchange, update.openedOrder);
    }

    protected void cancel(OrderType key, OrderUpdateEvent self) throws IOException {
        log.info("Cancel #{} {}", self.openExchange, self.openedOrder);
    }

    protected void updateOrder(OrderUpdateEvent self, OrderUpdateEvent event, OrderType type) throws ConnectionException, IOException {
        log.info("U.Cancel #{} {}", self.openExchange, self.openedOrder);
        log.info("U.Open #{} {}", event.openExchange, event.openedOrder);
    }

}
