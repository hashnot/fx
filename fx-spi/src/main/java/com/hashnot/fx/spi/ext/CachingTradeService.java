package com.hashnot.fx.spi.ext;

import com.google.common.base.Supplier;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class CachingTradeService extends AbstractTradeService {
    final private static Logger log = LoggerFactory.getLogger(CachingTradeService.class);

    final private Map<String, LimitOrder> openOrders;

    public CachingTradeService(Supplier<ITradeService> backend, Map<String, LimitOrder> openOrders) {
        super(backend);
        this.openOrders = openOrders;
    }

    @Override
    public boolean cancelOrder(String orderId) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        if (!openOrders.containsKey(orderId))
            throw new ExchangeException("Unknown order " + orderId);
        boolean result = super.cancelOrder(orderId);
        openOrders.remove(orderId);
        return result;
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        String id = super.placeLimitOrder(limitOrder);
        if (openOrders.containsKey(id))
            log.warn("ID {} present @{}", id, this);
        openOrders.put(id, limitOrder);
        return id;
    }

    public void cancelAll() {
        openOrders.forEach((k, v) -> {
            try {
                if (!cancelOrder(k))
                    log.warn("Unsuccessful cancel {}@{}", k, this);
            } catch (IOException e) {
                log.warn("Error while cancelling");
            }
        });
    }
}
