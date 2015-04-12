package com.hashnot.fx.strategy.pair;

import com.hashnot.xchange.event.trade.IOrderTradesListener;
import com.hashnot.xchange.event.trade.ITrackingTradeService;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.hashnot.xchange.ext.trade.OrderCancelEvent;
import com.hashnot.xchange.ext.trade.OrderPlacementEvent;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class SimpleOrderCloseStrategy implements IOrderTradesListener {
    final private static Logger log = LoggerFactory.getLogger(SimpleOrderCloseStrategy.class);
    IOrderTradesListener orderTradesListener;

    // TODO make less orders
    public void placeOrder(BigDecimal amountLimit, List<LimitOrder> closingOrders, ITrackingTradeService tradeService) {
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

    private void placeOrder(LimitOrder order, ITrackingTradeService tradeService) {
        tradeService.placeLimitOrder(order, orderTradesListener);
    }

    @Override
    public void limitOrderPlaced(OrderPlacementEvent<LimitOrder> orderPlacementEvent) {
        log.debug("limit order: {}" + orderPlacementEvent.id);
    }

    @Override
    public void marketOrderPlaced(OrderPlacementEvent<MarketOrder> orderPlacementEvent) {
        log.debug("market order: {}" + orderPlacementEvent.id);
    }

    @Override
    public void orderCanceled(OrderCancelEvent orderCancelEvent) {
        log.debug("order cancelled: {}" + orderCancelEvent.id);
    }

    @Override
    public void trade(UserTradeEvent userTradeEvent) {
        log.debug("trade: {}" + userTradeEvent.trade);
    }

    @Override
    public void orderPlacementFailed(LimitOrder order) {
        log.warn("Order failed {}", order);
    }

    @Override
    public void orderPlacementFailed(MarketOrder order) {
        log.warn("Order failed {}", order);
    }
}
