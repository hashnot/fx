package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ConnectionException;
import com.hashnot.fx.spi.ExchangeCache;
import com.xeiam.xchange.Exchange;
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

    public NoopOrderUpdater(Map<OrderType, OrderUpdateEvent> openOrders, Map<Exchange, ExchangeCache> ctx) {
        super(ctx, openOrders);
    }

    protected void open(OrderUpdateEvent update) throws IOException {
        log.info("Open @{} {} ", update.openExchange, update.openedOrder);
    }

    protected void cancel(OrderUpdateEvent self) throws IOException {
        log.info("Cancel @{} {}", self.openExchange, self.openedOrder);
    }

    protected void updateOrder(OrderUpdateEvent self, OrderUpdateEvent event) throws ConnectionException, IOException {
        log.info("U.Cancel @{} {}", self.openExchange, self.openedOrder);
        log.info("U.Open @{} {}", event.openExchange, event.openedOrder);
    }

}
