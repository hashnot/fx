package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ConnectionException;
import com.hashnot.fx.spi.ExchangeCache;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.xeiam.xchange.dto.Order.OrderType;

/**
 * @author Rafał Krupiński
 */
public class OrderUpdater implements IOrderUpdater {
    final private static Logger log = LoggerFactory.getLogger(OrderUpdater.class);

    private final Map<Exchange, ExchangeCache> ctx;
    private final Map<OrderType, OrderUpdateEvent> openOrders;

    public OrderUpdater(Map<Exchange, ExchangeCache> ctx, Map<OrderType, OrderUpdateEvent> openOrders) {
        this.ctx = ctx;
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
        update.openExchange.getPollingTradeService().placeLimitOrder(update.openedOrder);
    }

    protected void cancel(OrderUpdateEvent self) throws IOException {
        self.openExchange.getPollingTradeService().cancelOrder(self.openedOrder.getId());
    }

    protected void updateOrder(OrderUpdateEvent self, OrderUpdateEvent event) throws ConnectionException, IOException {
        PollingTradeService svc = self.openExchange.getPollingTradeService();
        svc.cancelOrder(self.openedOrder.getId());
        svc.placeLimitOrder(event.openedOrder);
    }

}
