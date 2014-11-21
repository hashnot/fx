package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.IUserTradeListener;
import com.hashnot.fx.framework.IUserTradeMonitor;
import com.hashnot.fx.framework.UserTradeEvent;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static com.hashnot.xchange.ext.util.Numbers.eq;

/**
 * @author Rafał Krupiński
 */
public class OrderManager implements IUserTradeListener {
    final private static Logger log = LoggerFactory.getLogger(OrderManager.class);

    final private IUserTradeMonitor tradeMonitor;

    final private SimpleOrderCloseStrategy orderCloseStrategy;

    final private Map<Exchange, IExchangeMonitor> monitors;

    volatile private OrderBinding orderBinding;

    public OrderManager(IUserTradeMonitor tradeMonitor, SimpleOrderCloseStrategy orderCloseStrategy, Map<Exchange, IExchangeMonitor> monitors) {
        this.tradeMonitor = tradeMonitor;
        this.orderCloseStrategy = orderCloseStrategy;
        this.monitors = monitors;
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

        IAsyncTradeService tradeService = monitors.get(update.openExchange).getTradeService();
        tradeService.placeLimitOrder(update.openedOrder, (idf) -> {
            String id = null;
            try {
                id = idf.get();
            } catch (Exception e) {
                if (update.openOrderId == null) {
                    log.warn("Error from {}", update.openExchange, e);
                    orderBinding = null;
                } else {
                    log.warn("Error from {} but have order ID {}", update.openExchange, update.openOrderId, e);
                }
            }
            // cancel might have been called during placeLimitOrder. If so, orderBinding is null
            if (orderBinding == null) {
                tradeService.cancelOrder(id, (s) -> {
                });
            } else
                update.openOrderId = id;
        });
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
        IAsyncTradeService tradeService = monitors.get(orderBinding.openExchange).getTradeService();
        tradeService.cancelOrder(orderId, (success) -> {
        });
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

    public LimitOrder getOpenOrder() {
        return orderBinding == null ? null : orderBinding.openedOrder;
    }
}
