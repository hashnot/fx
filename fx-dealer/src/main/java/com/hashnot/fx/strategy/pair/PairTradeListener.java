package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.Operations;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.trade.IUserTradeListener;
import com.hashnot.xchange.event.trade.IUserTradeMonitor;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.hashnot.xchange.ext.util.Numbers.eq;

/**
 * @author Rafał Krupiński
 */
public class PairTradeListener implements IUserTradeListener {
    final private static Logger log = LoggerFactory.getLogger(PairTradeListener.class);

    final private SimpleOrderCloseStrategy orderCloseStrategy;

    final private Map<Exchange, IExchangeMonitor> monitors;

    final private DealerData data;

    public PairTradeListener(SimpleOrderCloseStrategy orderCloseStrategy, Map<Exchange, IExchangeMonitor> monitors, DealerData data) {
        this.orderCloseStrategy = orderCloseStrategy;
        this.monitors = monitors;
        this.data = data;
    }

    public boolean isActive() {
        return data.orderBinding != null;
    }

    synchronized public void update(List<LimitOrder> orders) {
        data.orderBinding.closingOrders = orders;
    }

    /*
     old value is called self, new - evt
     */
    synchronized public void update(OrderBinding evt) {
        if (data.orderBinding == null) {
            open(evt);
        } else if (!isIgnoreUpdate(data.orderBinding, evt)) {
            if (evt.openExchange.equals(data.orderBinding.openExchange)) {
                updateOrder(evt.openedOrder);
            } else {
                cancel();
                open(evt);
            }
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

    private void updateOrder(LimitOrder order) {
        IAsyncTradeService tradeService = getTradeService(data.orderBinding.openExchange);
        Operations.updateOrder(tradeService, data.orderBinding.openOrderId, order, (idf) -> {
            try {
                data.orderBinding.openOrderId = idf.get();
            } catch (InterruptedException e) {
                log.warn("Interrupted", e);
                data.orderBinding = null;
            } catch (ExecutionException e) {
                log.warn("Error from {}", tradeService, e.getCause());
                data.orderBinding = null;
            }
        });
    }

    protected void open(OrderBinding update) {
        if (data.orderBinding != null)
            throw new IllegalStateException("Seems order already open " + data.orderBinding.openedOrder.getType());

        getTradeMonitor(update.openExchange).addTradeListener(this);

        // Make OrderManager "active" before the order is actually placed.
        // This prevents another thread to try to activate it while waiting for the response

        data.orderBinding = update;

        IAsyncTradeService tradeService = getTradeService(update.openExchange);
        tradeService.placeLimitOrder(update.openedOrder, (idf) -> {
            String id = null;
            try {
                id = idf.get();
            } catch (ExecutionException x) {
                Throwable e = x.getCause();
                if (update.openOrderId == null) {
                    log.warn("Error from {}", tradeService, e);
                    data.orderBinding = null;
                } else {
                    log.warn("Error from {} but have order ID {}", tradeService, update.openOrderId, e);
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted!", e);
            }
            // cancel might have been called during placeLimitOrder. If so, orderBinding is null
            if (data.orderBinding == null) {
                tradeService.cancelOrder(id, (s) -> {
                });
            } else
                update.openOrderId = id;
        });
    }

    synchronized public void cancel() {
        if (!isActive()) {
            log.warn("Cancelling inactive order manager");
            return;
        }
        if (data.orderBinding.openOrderId == null) {
            log.warn("Cancelling order bind while still opening order");
            data.orderBinding = null;
            return;
        }
        log.info("Cancel @{} {} {}", data.orderBinding.openExchange, data.orderBinding.openOrderId, data.orderBinding.openedOrder);
        IAsyncTradeService tradeService = getTradeService(data.orderBinding.openExchange);
        tradeService.cancelOrder(data.orderBinding.openOrderId, (future) -> {
            try {
                Boolean success = future.get();
                data.orderBinding = null;
                if (!success) {
                    log.warn("No such order");
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted!", e);
            } catch (ExecutionException e) {
                log.warn("Error from {}", tradeService, e.getCause());
            }
        });
    }

    @Override
    public synchronized void trade(UserTradeEvent evt) {
        UserTrade trade = evt.trade;

        if (data.orderBinding == null || !trade.getOrderId().equals(data.orderBinding.openOrderId)) {
            log.debug("Trade of an unknown order {} @{}", trade, evt.source);
            return;
        }

        if (!evt.source.equals(data.orderBinding.openExchange)) {
            log.warn("Trade of untracked exchange {}@{}", trade, evt.source);
            return;
        }

        orderCloseStrategy.placeOrder(trade.getTradableAmount(), data.orderBinding.closingOrders, monitors.get(data.orderBinding.closeExchange).getAsyncExchange().getTradeService());

        if (evt.current == null) {
            getTradeMonitor(data.orderBinding.openExchange).removeTradeListener(this);
            data.orderBinding = null;
        }
    }

    public LimitOrder getOpenOrder() {
        return data.orderBinding == null ? null : data.orderBinding.openedOrder;
    }

    private IUserTradeMonitor getTradeMonitor(Exchange exchange) {
        return monitors.get(exchange).getOrderTracker();
    }

    private IAsyncTradeService getTradeService(Exchange exchange) {
        return monitors.get(exchange).getAsyncExchange().getTradeService();
    }

}
