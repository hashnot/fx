package com.hashnot.fx.dealer;

import com.hashnot.fx.framework.*;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static com.hashnot.xchange.ext.util.Numbers.eq;

/**
 * @author Rafał Krupiński
 */
public class OrderManager implements IUserTradeListener {
    final private static Logger log = LoggerFactory.getLogger(OrderManager.class);

    final private IUserTradeMonitor tradeMonitor;

    final private SimpleOrderCloseStrategy orderCloseStrategy;

    volatile private OrderBinding orderBinding;

    public OrderManager(IUserTradeMonitor tradeMonitor, SimpleOrderCloseStrategy orderCloseStrategy) {
        this.tradeMonitor = tradeMonitor;
        this.orderCloseStrategy = orderCloseStrategy;
    }

    public boolean isActive() {
        return orderBinding != null;
    }

    public void update(List<LimitOrder> orders) {
        orderBinding.closingOrders = orders;
    }

    /*
     old value is called self, new - evt
     */
    public void update(OrderBinding evt) {
        if (orderBinding == null) {
            open(evt);
        } else if (!isIgnoreUpdate(orderBinding, evt)) {
            cancel();
            // instead of update, cancel and let next iteration opens it again
            open(evt);
        } else {
            log.debug("Update order ignored");
        }
    }

    /**
     * Can the update order be safely ignored
     */
    private boolean isIgnoreUpdate(OrderBinding existing, OrderBinding incoming) {
        if (!existing.openExchange.equals(incoming.openExchange))
            return false;

        LimitOrder o1 = existing.openedOrder;
        LimitOrder o2 = incoming.openedOrder;

        return o1.getCurrencyPair().equals(o2.getCurrencyPair()) && o1.getType() == o2.getType()
                && eq(o1.getLimitPrice(), o2.getLimitPrice())
                && o1.getTradableAmount().compareTo(o2.getTradableAmount()) <= 0;
    }

    protected void open(OrderBinding update) {
        if (orderBinding != null)
            throw new IllegalStateException("Seems order already open " + orderBinding.openedOrder.getType());

        tradeMonitor.addTradeListener(this, update.openExchange);

        // Make OrderManager "active" before the order is actually placed.
        // This prevents another thread to try to activate it while waiting for the response

        orderBinding = update;
        try {
            String id = placeLimitOrder(update.openedOrder, update.openExchange.getPollingTradeService());

            // cancel might have been called during placeLimitOrder. If so, orderBinding is null
            if (orderBinding == null) {
                cancel(id, update);
            } else
                update.openOrderId = id;
        } catch (ExchangeException | ConnectionException e) {
            if (update.openOrderId == null) {
                log.warn("Error from {}", update.openExchange, e);
                orderBinding = null;
            } else {
                log.warn("Error from {} but have order ID {}", update.openExchange, update.openOrderId, e);
            }
        }
    }

    public void cancel() {
        if (!isActive()) {
            log.warn("Cancelling inactive order manager");
            return;
        }
        if (orderBinding.openOrderId == null) {
            log.warn("Cancelling order bind while still opening order");
            orderBinding = null;
            return;
        }
        log.info("Cancel @{} {} {}", orderBinding.openExchange, orderBinding.openOrderId, orderBinding.openedOrder);
        cancel(orderBinding.openOrderId, orderBinding);
        orderBinding = null;
    }

    protected void cancel(String orderId, OrderBinding orderBinding) {
        try {
            orderBinding.openExchange.getPollingTradeService().cancelOrder(orderId);
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    @Override
    public synchronized void trade(UserTradeEvent evt) {
        UserTrade trade = evt.trade;

        if (orderBinding == null || !trade.getOrderId().equals(orderBinding.openOrderId)) {
            log.debug("Trade of an unknown order {} @{}", trade, evt.source);
            return;
        }

        if (!evt.source.equals(orderBinding.openExchange)) {
            log.warn("Trade of untracked exchange {}@{}", trade, evt.source);
            return;
        }

        orderCloseStrategy.placeOrder(trade.getTradableAmount(), orderBinding.closingOrders, orderBinding.closeExchange);

        if (evt.current == null) {
            tradeMonitor.removeTradeListener(this, orderBinding.openExchange);
            orderBinding = null;
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

    public LimitOrder getOpenOrder() {
        return orderBinding == null ? null : orderBinding.openedOrder;
    }
}
