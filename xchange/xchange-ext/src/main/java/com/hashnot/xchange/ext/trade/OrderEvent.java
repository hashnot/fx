package com.hashnot.xchange.ext.trade;

import com.hashnot.xchange.ext.event.Event;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;

/**
 * @author Rafał Krupiński
 */
public class OrderEvent extends Event<Exchange> {
    public final String id;
    public final LimitOrder order;

    public OrderEvent(String id, LimitOrder order, Exchange source) {
        super(source);
        this.id = id;
        this.order = order;
    }
}