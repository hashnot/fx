package com.hashnot.fx.dealer;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.util.List;

/**
 * @author Rafał Krupiński
 */
public class OrderBinding {
    public final Exchange openExchange;
    public final Exchange closeExchange;
    public final LimitOrder openedOrder;

    public String openOrderId;
    public List<LimitOrder> closingOrders;

    // Unneded, because we're closing orders according to incoming trades and we also know when open order is closed
    // public BigDecimal filled = BigDecimal.ZERO;

    public OrderBinding(Exchange openExchange, Exchange closeExchange, LimitOrder openedOrder, List<LimitOrder> closingOrders) {
        this.openExchange = openExchange;
        this.closeExchange = closeExchange;
        this.openedOrder = openedOrder;
        this.closingOrders = closingOrders;
    }

    @Override
    public String toString() {
        return "OrderUpdateEvent{" +
                "openExchange=" + openExchange +
                ", closeExchange=" + closeExchange +
                ", openedOrder=" + openedOrder +
                ", openOrderId='" + openOrderId + '\'' +
                ", closingOrders=" + closingOrders +
                '}';
    }
}
