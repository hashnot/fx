package com.hashnot.fx.framework;

import com.hashnot.fx.ext.ITradeListener;
import com.hashnot.fx.spi.ConnectionException;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hashnot.fx.util.Numbers.isEqual;
import static com.xeiam.xchange.dto.Order.OrderType;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class OrderManager implements IOrderUpdater, ITradeListener {
    final private static Logger log = LoggerFactory.getLogger(OrderManager.class);
    final private Map<OrderType, OrderUpdateEvent> openOrders = new HashMap<>();

    /*
     old value is called self, new - evt
     */
    @Override
    public void update(OrderUpdateEvent evt) {
        try {
            if (evt.clear != null) {
                OrderUpdateEvent self = openOrders.get(evt.clear);
                if (self != null) {
                    cancel(evt.clear, self);
                }
                return;
            }
            OrderType key = evt.openedOrder.getType();
            OrderUpdateEvent self = openOrders.get(key);

            if (self == null) {
                open(key, evt);
            } else if (!isIgnoreUpdate(self, evt)) {
                updateOrder(self, evt, key);
            } else {
                log.debug("Update order ignored");
            }
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    /**
     * Can the update order be safely ignored
     */
    private boolean isIgnoreUpdate(OrderUpdateEvent existing, OrderUpdateEvent incoming) {
        if (!existing.openExchange.equals(incoming.openExchange))
            return false;

        LimitOrder o1 = existing.openedOrder;
        LimitOrder o2 = incoming.openedOrder;

        return o1.getCurrencyPair().equals(o2.getCurrencyPair()) && o1.getType() == o2.getType()
                && isEqual(o1.getLimitPrice(), o2.getLimitPrice())
                && o1.getTradableAmount().compareTo(o2.getTradableAmount()) <= 0;

    }

    protected void open(OrderType type, OrderUpdateEvent update) throws IOException {
        if (openOrders.containsKey(type))
            throw new IllegalStateException("Seems order already open " + type);
        log.info("Open @{} {} ", update.openExchange, update.openedOrder);
        update.openOrderId = update.openExchange.getPollingTradeService().placeLimitOrder(update.openedOrder);
        log.info("Opened {}", update.openOrderId);
        openOrders.put(type, update);
    }

    protected void cancel(OrderType key, OrderUpdateEvent self) throws IOException {
        log.info("Cancel @{} {} {}", self.openExchange, self.openOrderId, self.openedOrder);
        self.openExchange.getPollingTradeService().cancelOrder(self.openOrderId);
        self.openOrderId = null;
        openOrders.remove(key);
    }

    protected void updateOrder(OrderUpdateEvent self, OrderUpdateEvent event, OrderType type) throws ConnectionException, IOException {
        cancel(type, self);
        //open(type, event);
    }

    @Override
    public void trade(LimitOrder openedOrder, Trade trade, LimitOrder currentOrder) {
        List<LimitOrder> closingOrders = openOrders.get(trade.getType()).closingOrders;
        Order.OrderType type = closingOrders.get(0).getType();
        PollingTradeService tradeService = openOrders.get(type).closeExchange.getPollingTradeService();

        if (currentOrder == null)
            close(closingOrders, tradeService);
        else {
            BigDecimal amount = openedOrder.getTradableAmount().subtract(currentOrder.getTradableAmount());
            close(closingOrders, amount, tradeService);
        }
    }

    private void close(List<LimitOrder> orders, BigDecimal amountLimit, PollingTradeService tradeService) {
        BigDecimal totalAmount = ZERO;
        try {
            for (LimitOrder order : orders) {
                BigDecimal newAmount = totalAmount.add(order.getTradableAmount());
                int cmp = newAmount.compareTo(amountLimit);
                if (cmp == 0) {
                    placeLimitOrder(order, tradeService);
                    break;
                } else if (cmp <= 0) {
                    placeLimitOrder(order, tradeService);
                    totalAmount = newAmount;
                } else {
                    BigDecimal amount = amountLimit.subtract(totalAmount);
                    placeLimitOrder(Orders.withAmount(order, amount), tradeService);
                    break;
                }
            }
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    private void close(List<LimitOrder> orders, PollingTradeService pollingTradeService) {
        try {
            for (LimitOrder order : orders) {
                placeLimitOrder(order, pollingTradeService);
            }
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    protected void placeLimitOrder(LimitOrder order, PollingTradeService tradeService) throws IOException {
        log.info("Open {} @{}", order, tradeService.getClass().getSimpleName());
        tradeService.placeLimitOrder(order);
    }
}
