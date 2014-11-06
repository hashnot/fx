package com.hashnot.fx.framework.impl;

import com.hashnot.fx.framework.ILimitOrderPlacementListener;
import com.hashnot.fx.framework.ITradeService;
import com.hashnot.fx.framework.OrderEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.dto.trade.LimitOrder;
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
    public boolean cancelOrder(String orderId) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        boolean result = super.cancelOrder(orderId);
        if (openOrders.remove(orderId) == null)
            log.info("Unknown order {}", orderId);
        return result;
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        Exchange exchange = this.exchange.get();
        String id = exchange.getPollingTradeService().placeLimitOrder(limitOrder);
        if (openOrders.containsKey(id))
            log.warn("ID {} present @{}", id, exchange);
        openOrders.put(id, limitOrder);

        multiplex(listeners, new OrderEvent(id, limitOrder, exchange), (l, e) -> l.limitOrderPlaced(e));

        return id;
    }

    public void cancelAll() {
        openOrders.forEach((k, v) -> {
            log.info("Cancel {} at shutdown", k);
            try {
                if (!cancelOrder(k))
                    log.warn("Unsuccessful cancel {}@{}", k, this);
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

}
