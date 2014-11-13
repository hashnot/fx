package com.hashnot.fx.framework.impl;

import com.hashnot.fx.framework.ILimitOrderPlacementListener;
import com.hashnot.fx.framework.ITradeService;
import com.hashnot.fx.framework.OrderCancelEvent;
import com.hashnot.fx.framework.OrderEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;

/**
 * @author Rafał Krupiński
 */
public class TradeService extends AbstractTradeService implements ITradeService {
    final private static Logger log = LoggerFactory.getLogger(TradeService.class);

    final private Map<String, LimitOrder> openOrders = new ConcurrentHashMap<>();

    private Set<ILimitOrderPlacementListener> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public TradeService(Supplier<Exchange> backend) {
        super(backend);
    }

    @Override
    public boolean cancelOrder(String orderId) throws IOException {
        Exchange x = exchange.get();
        log.debug("Cancel {}@{}", orderId, this);
        boolean result = x.getPollingTradeService().cancelOrder(orderId);
        if (openOrders.remove(orderId) == null)
            log.info("Unknown order {}", orderId);
        else if (!result)
            log.warn("Unsuccessful cancel order {}@{}", orderId, this);

        if (result)
            multiplex(listeners, new OrderCancelEvent(orderId, x), (l, e) -> l.orderCanceled(e));

        return result;
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
        Exchange exchange = this.exchange.get();
        String id = exchange.getPollingTradeService().placeLimitOrder(limitOrder);
        if (openOrders.containsKey(id))
            log.warn("ID {} present @{}", id, exchange);
        openOrders.put(id, limitOrder);

        multiplex(listeners, new OrderEvent(id, limitOrder, exchange), (l, e) -> l.limitOrderPlaced(e));

        return id;
    }

    // TODO move to OrderTracker
    public void cancelAll() {
        log.info("Cancel all at shutdown");
        openOrders.forEach((k, v) -> {
            try {
                cancelOrder(k);
            } catch (IOException e) {
                log.warn("Error while cancelling {}", k, e);
            }
        });
    }

    @Override
    public void addLimitOrderPlacedListener(ILimitOrderPlacementListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeLimitOrderPlacedListener(ILimitOrderPlacementListener listener) {
        listeners.remove(listener);
    }

    @Override
    public String toString() {
        PollingTradeService backend = backend();
        return "TradeService(" + backend.getClass().getSimpleName() + "#" + System.identityHashCode(backend) + ")";
    }
}
