package com.hashnot.fx.framework;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.util.List;

/**
 * @author Rafał Krupiński
 */
public class OrderUpdateEvent {
    public final Exchange openExchange;
    public final Exchange closeExchange;
    public final LimitOrder openedOrder;
    public final List<LimitOrder> closingOrders;

    public OrderUpdateEvent(Exchange openExchange, Exchange closeExchange, LimitOrder openedOrder, List<LimitOrder> closingOrders) {
        this.openExchange = openExchange;
        this.closeExchange = closeExchange;
        this.openedOrder = openedOrder;
        this.closingOrders = closingOrders;
    }
}
