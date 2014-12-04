package com.hashnot.xchange.ext.trade;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;

/**
 * @author Rafał Krupiński
 */
public class TradeService extends AbstractTradeService implements ITradeService {
    final private static Logger log = LoggerFactory.getLogger(TradeService.class);

    private Set<IOrderPlacementListener> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public TradeService(Supplier<Exchange> backend) {
        super(backend);
    }

    @Override
    public boolean cancelOrder(String orderId) throws IOException {
        Exchange x = exchange.get();
        log.debug("Cancel {}@{}", orderId, this);
        boolean result = x.getPollingTradeService().cancelOrder(orderId);
        multiplex(listeners, new OrderCancelEvent(orderId, x), IOrderPlacementListener::orderCanceled);
        if (!result) {
            log.warn("Unsuccessful cancel order {}@{}", orderId, this);
        }

        return result;
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
        Exchange exchange = this.exchange.get();
        String id = exchange.getPollingTradeService().placeLimitOrder(limitOrder);

        multiplex(listeners, new OrderEvent<>(id, limitOrder, exchange), IOrderPlacementListener::limitOrderPlaced);

        return id;
    }

    @Override
    public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
        Exchange exchange = this.exchange.get();
        String id = exchange.getPollingTradeService().placeMarketOrder(marketOrder);

        multiplex(listeners, new OrderEvent<>(id, marketOrder, exchange), IOrderPlacementListener::marketOrderPlaced);

        return id;
    }

    @Override
    public void addLimitOrderPlacedListener(IOrderPlacementListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeLimitOrderPlacedListener(IOrderPlacementListener listener) {
        listeners.remove(listener);
    }

    @Override
    public String toString() {
        PollingTradeService backend = backend();
        return "TradeService(" + backend.getClass().getSimpleName() + "#" + System.identityHashCode(backend) + ")";
    }
}
