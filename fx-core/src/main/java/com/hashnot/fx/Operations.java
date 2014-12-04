package com.hashnot.fx;

import com.google.common.util.concurrent.SettableFuture;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class Operations {
    final private static Logger log = LoggerFactory.getLogger(Operations.class);

    public static void updateOrder(IAsyncTradeService tradeService, String orderId, LimitOrder order, Consumer<Future<String>> consumer) {
        tradeService.cancelOrder(orderId, success -> {
            SettableFuture<String> result = SettableFuture.create();
            try {
                if (success.get()) {
                    tradeService.placeLimitOrder(order, consumer);
                } else {
                    result.setException(new IllegalStateException("No such order"));
                    consumer.accept(result);
                }
            } catch (ExecutionException e) {
                result.setException(e.getCause());
                consumer.accept(result);
            } catch (InterruptedException e) {
                log.warn("Interrupted!", e);
            }
        });
    }

    /**
     * @param order order to revert
     */
    public static void cancelOrRevert(String id, Order order, IAsyncTradeService tradeService, Consumer<Future<String>> consumer) {
        tradeService.cancelOrder(id, (future) -> {
            Boolean canceled;
            try {
                canceled = future.get();
            } catch (InterruptedException e) {
                log.warn("Interrupted!", e);
                return;
            } catch (ExecutionException e) {
                log.warn("Error from {}", e.getCause());
                canceled = false;
            }
            if (!canceled)
                close(order, tradeService, consumer);
        });
    }

    public static void close(Order order, IAsyncTradeService tradeService, Consumer<Future<String>> consumer) {
        MarketOrder marketOrder = MarketOrder.Builder.from(order).orderType(Orders.revert(order.getType())).build();
        tradeService.placeMarketOrder(marketOrder, consumer);
    }
}
