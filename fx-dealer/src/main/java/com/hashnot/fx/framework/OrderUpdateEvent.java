package com.hashnot.fx.framework;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;

/**
 * @author Rafał Krupiński
 */
public class OrderUpdateEvent {
    public final Exchange exchange;
    public final LimitOrder order;

    public OrderUpdateEvent(Exchange exchange, LimitOrder order) {
        this.exchange = exchange;
        this.order = order;
    }

    @Override
    public String toString() {
        return order + " @" + exchange;
    }
}
