package com.hashnot.xchange.ext.trade;

import com.hashnot.xchange.ext.event.Event;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.Order;

/**
 * @author Rafał Krupiński
 */
public class OrderEvent<T extends Order> extends Event<Exchange> {
    public final String id;
    public final T order;

    public OrderEvent(String id, T order, Exchange source) {
        super(source);
        this.id = id;
        this.order = order;
    }
}
