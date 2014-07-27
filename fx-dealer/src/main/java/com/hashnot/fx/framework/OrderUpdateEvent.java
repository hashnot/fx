package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.util.List;

/**
 * @author Rafał Krupiński
 */
public class OrderUpdateEvent {
    public final IExchange openExchange;
    public final IExchange closeExchange;
    public final LimitOrder openedOrder;
    public String openOrderId;
    public final List<LimitOrder> closingOrders;

    public final Order.OrderType clear;

    public OrderUpdateEvent(IExchange openExchange, IExchange closeExchange, LimitOrder openedOrder, List<LimitOrder> closingOrders) {
        this.openExchange = openExchange;
        this.closeExchange = closeExchange;
        this.openedOrder = openedOrder;
        this.closingOrders = closingOrders;

        clear = null;
    }

    public OrderUpdateEvent(Order.OrderType clear) {
        this.clear = clear;

        this.openExchange = null;
        this.closeExchange = null;
        this.openedOrder = null;
        this.closingOrders = null;
    }
}
