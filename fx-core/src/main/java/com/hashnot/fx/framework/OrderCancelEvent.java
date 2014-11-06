package com.hashnot.fx.framework;

import com.hashnot.xchange.event.Event;
import com.xeiam.xchange.Exchange;

/**
 * @author Rafał Krupiński
 */
public class OrderCancelEvent extends Event<Exchange> {
    public final String id;

    public OrderCancelEvent(String id, Exchange source) {
        super(source);
        this.id = id;
    }
}
