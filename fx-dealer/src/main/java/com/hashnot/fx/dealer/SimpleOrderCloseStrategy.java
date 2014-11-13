package com.hashnot.fx.dealer;

import com.hashnot.fx.framework.ConnectionException;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class SimpleOrderCloseStrategy {
    final private static Logger log = LoggerFactory.getLogger(SimpleOrderCloseStrategy.class);


    // TODO make less orders
    public void placeOrder(BigDecimal amountLimit, List<LimitOrder> closingOrders, Exchange exchange) {
        BigDecimal totalAmount = ZERO;
        for (LimitOrder order : closingOrders) {
            PollingTradeService tradeService = exchange.getPollingTradeService();
            BigDecimal newAmount = totalAmount.add(order.getTradableAmount());
            int cmp = newAmount.compareTo(amountLimit);
            LimitOrder.Builder myOrder = from(order).orderType(Orders.revert(order.getType()));
            if (cmp == 0) {
                closeOrder(myOrder.build(), tradeService);
                break;
            } else if (cmp < 0) {
                closeOrder(myOrder.build(), tradeService);
                totalAmount = newAmount;
            } else {
                BigDecimal amount = amountLimit.subtract(totalAmount);
                closeOrder(myOrder.tradableAmount(amount).build(), tradeService);
                break;
            }
        }
    }

    private String closeOrder(LimitOrder order, PollingTradeService tradeService) {
        try {
            String id = tradeService.placeLimitOrder(order);
            log.info("Open {} {} @{}", id, order, tradeService);
            return id;
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

}
