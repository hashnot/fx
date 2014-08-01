package com.hashnot.fx.framework;

import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class NoopOrderClosedListener extends OrderClosedListener {
    final private static Logger log = LoggerFactory.getLogger(NoopOrderClosedListener.class);

    public NoopOrderClosedListener(Map<Order.OrderType, OrderUpdateEvent> openOrders) {
        super(openOrders);
    }

    @Override
    protected void placeLimitOrder(LimitOrder order, PollingTradeService tradeService) throws IOException {
        log.info("Open {} #{}", order, tradeService.getClass().getSimpleName());
    }
}
