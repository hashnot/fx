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
    public final List<LimitOrder>openedOrders;
    public final List<LimitOrder>closingOrders;

    public OrderUpdateEvent(Exchange openExchange, Exchange closeExchange, List<LimitOrder> openedOrders, List<LimitOrder> closingOrders) {
        this.openExchange = openExchange;
        this.closeExchange = closeExchange;
        this.openedOrders = openedOrders;
        this.closingOrders = closingOrders;
    }
}
