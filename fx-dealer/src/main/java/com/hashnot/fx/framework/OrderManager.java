package com.hashnot.fx.framework;

import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.hashnot.xchange.ext.util.Numbers.eq;
import static com.xeiam.xchange.dto.Order.OrderType;
import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class OrderManager implements IOrderUpdater, IUserTradeListener {
    final private static Logger log = LoggerFactory.getLogger(OrderManager.class);

    final private IUserTradeMonitor tradeMonitor;

    final private Map<OrderType, OrderUpdateEvent> openOrders = new EnumMap<>(OrderType.class);

    public OrderManager(IUserTradeMonitor tradeMonitor) {
        this.tradeMonitor = tradeMonitor;
    }

    /*
     old value is called self, new - evt
     */
    @Override
    public void update(OrderUpdateEvent evt) {
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
                cancel(key, self);
            // instead of update, cancel and let next iteration opens it again
            open(key, evt);
            } else {
                log.debug("Update order ignored");
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
                && eq(o1.getLimitPrice(), o2.getLimitPrice())
                && o1.getTradableAmount().compareTo(o2.getTradableAmount()) <= 0;
    }

    protected void open(OrderType type, OrderUpdateEvent update) {
        if (openOrders.containsKey(type))
            throw new IllegalStateException("Seems order already open " + type);

        tradeMonitor.addTradeListener(this, update.openExchange);

        update.openOrderId = placeLimitOrder(update.openedOrder, update.openExchange.getPollingTradeService());
        openOrders.put(type, update);
    }

    protected void cancel(OrderType key, OrderUpdateEvent self) {
        log.info("Cancel @{} {} {}", self.openExchange, self.openOrderId, self.openedOrder);
        try {
            self.openExchange.getPollingTradeService().cancelOrder(self.openOrderId);
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
        self.openOrderId = null;
        openOrders.remove(key);
    }

    @Override
    public void trade(UserTradeEvent evt) {
        UserTrade trade = evt.trade;
        OrderUpdateEvent event = openOrders.get(trade.getType());

        if (event == null || !trade.getOrderId().equals(event.openOrderId)) {
            log.debug("Trade of an unknown order {} @{}", trade, evt.source);
            return;
        }

        if (!evt.source.equals(event.openExchange)) {
            log.warn("Trade of untracked exchange {}@{}", trade, evt.source);
            return;
        }

        // TODO unregister listener when order is filled
        close(event.closingOrders, trade.getTradableAmount(), event.closeExchange.getPollingTradeService());
    }

    private void close(List<LimitOrder> orders, BigDecimal amountLimit, PollingTradeService tradeService) {
        BigDecimal totalAmount = ZERO;
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
                    placeLimitOrder(from(order).tradableAmount(amount).build(), tradeService);
                    break;
                }
            }
    }

    protected String placeLimitOrder(LimitOrder order, PollingTradeService tradeService) {
        try {
        String id = tradeService.placeLimitOrder(order);
        log.info("Open {} {} @{}", id, order, tradeService);
        return id;
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }
}
