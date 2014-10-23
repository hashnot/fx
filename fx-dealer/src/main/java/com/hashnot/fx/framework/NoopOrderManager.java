package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ConnectionException;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.xeiam.xchange.dto.Order.OrderType;

/**
 * @author Rafał Krupiński
 */
public class NoopOrderManager extends OrderManager {
    final private static Logger log = LoggerFactory.getLogger(NoopOrderManager.class);

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

    @Override
    public void trade(LimitOrder openedOrder, Trade trade, LimitOrder currentOrder) {
        log.info("Trade {} {}", trade);
    }

    @Override
    protected void placeLimitOrder(LimitOrder order, PollingTradeService tradeService) throws IOException {
        log.info("New order {}", order);
    }

}
