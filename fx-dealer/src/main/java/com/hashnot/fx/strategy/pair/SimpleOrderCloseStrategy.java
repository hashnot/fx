package com.hashnot.fx.strategy.pair;

import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class SimpleOrderCloseStrategy {
    final private static Logger log = LoggerFactory.getLogger(SimpleOrderCloseStrategy.class);


    // TODO make less orders
    public void placeOrder(BigDecimal amountLimit, List<LimitOrder> closingOrders, IAsyncTradeService tradeService) {
        BigDecimal totalAmount = ZERO;
        for (LimitOrder order : closingOrders) {
            BigDecimal newAmount = totalAmount.add(order.getTradableAmount());
            int cmp = newAmount.compareTo(amountLimit);
            LimitOrder.Builder myOrder = from(order).orderType(Orders.revert(order.getType()));
            if (cmp == 0) {
                placeOrder(myOrder.build(), tradeService);
                break;
            } else if (cmp < 0) {
                placeOrder(myOrder.build(), tradeService);
                totalAmount = newAmount;
            } else {
                BigDecimal amount = amountLimit.subtract(totalAmount);
                placeOrder(myOrder.tradableAmount(amount).build(), tradeService);
                break;
            }
        }
    }

    private void placeOrder(LimitOrder order, IAsyncTradeService tradeService) {
        tradeService.placeLimitOrder(order, (future) -> {
            try {
                String id = future.get();
                log.info("Open {} {} @{}", id, order, tradeService);
            } catch (InterruptedException e) {
                log.warn("Interrupted!", e);
            } catch (ExecutionException e) {
                log.warn("Error from {}", tradeService, e);
            }
        });
    }

}
