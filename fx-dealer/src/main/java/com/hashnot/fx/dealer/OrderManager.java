package com.hashnot.fx.dealer;

import com.hashnot.fx.framework.*;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static com.hashnot.xchange.ext.util.Numbers.eq;
import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class OrderManager implements IUserTradeListener {
    final private static Logger log = LoggerFactory.getLogger(OrderManager.class);

    final private IUserTradeMonitor tradeMonitor;

    private OrderBinding orderBinding;

    public OrderManager(IUserTradeMonitor tradeMonitor) {
        this.tradeMonitor = tradeMonitor;
    }

    public boolean isActive() {
        return orderBinding != null;
    }

    // TODO make closing orders not reverted, revert at closing time
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

        update.openOrderId = placeLimitOrder(update.openedOrder, update.openExchange.getPollingTradeService());
        orderBinding = update;
    }

    public void cancel() {
        if(!isActive()){
            log.warn("Cancelling inactive order manager");
            return;
        }
        log.info("Cancel @{} {} {}", orderBinding.openExchange, orderBinding.openOrderId, orderBinding.openedOrder);
        try {
            orderBinding.openExchange.getPollingTradeService().cancelOrder(orderBinding.openOrderId);
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
        orderBinding = null;
    }

    @Override
    public void trade(UserTradeEvent evt) {
        UserTrade trade = evt.trade;

        if (orderBinding == null || !trade.getOrderId().equals(orderBinding.openOrderId)) {
            log.debug("Trade of an unknown order {} @{}", trade, evt.source);
            return;
        }

        if (!evt.source.equals(orderBinding.openExchange)) {
            log.warn("Trade of untracked exchange {}@{}", trade, evt.source);
            return;
        }

        close(trade.getTradableAmount());

        if (evt.current == null) {
            tradeMonitor.removeTradeListener(this, orderBinding.openExchange);
            orderBinding = null;
        }
    }

    private void close(BigDecimal amountLimit) {
        PollingTradeService tradeService = orderBinding.closeExchange.getPollingTradeService();
        BigDecimal totalAmount = ZERO;
        for (LimitOrder order : orderBinding.closingOrders) {
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
