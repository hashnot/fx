package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ConnectionException;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class OrderClosedListener implements IOrderClosedListener {
    final private Map<Order.OrderType, OrderUpdateEvent> openOrders;


    public OrderClosedListener(Map<Order.OrderType, OrderUpdateEvent> openOrders) {
        this.openOrders = openOrders;
    }

    @Override
    public void orderClosed(LimitOrder openedOrder, LimitOrder currentOrder) {
        List<LimitOrder> closingOrders = openOrders.get(openedOrder.getType()).closingOrders;

        PollingTradeService tradeService = openOrders.get(closingOrders.get(0).getType()).closeExchange.getPollingTradeService();
        if (currentOrder == null)
            close(closingOrders, tradeService);
        else
            close(closingOrders, openedOrder.getTradableAmount().subtract(currentOrder.getTradableAmount()), tradeService);
    }

    private void close(List<LimitOrder> orders, BigDecimal amountLimit, PollingTradeService tradeService) {
        BigDecimal totalAmount = ZERO;
        try {
            for (LimitOrder order : orders) {
                BigDecimal newAmount = totalAmount.add(order.getTradableAmount());
                int cmp = newAmount.compareTo(amountLimit);
                if (cmp == 0) {
                    tradeService.placeLimitOrder(order);
                    break;
                } else if (cmp <= 0) {
                    tradeService.placeLimitOrder(order);
                    totalAmount = newAmount;
                } else {
                    BigDecimal amount = amountLimit.subtract(totalAmount);
                    tradeService.placeLimitOrder(Orders.withAmount(order, amount));
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
                pollingTradeService.placeLimitOrder(order);
            }
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }
}
