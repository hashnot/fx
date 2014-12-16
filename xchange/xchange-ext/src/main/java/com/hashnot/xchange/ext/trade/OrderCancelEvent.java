package com.hashnot.xchange.ext.trade;

import com.hashnot.xchange.ext.event.Event;
import com.xeiam.xchange.Exchange;

/**
 * @author Rafał Krupiński
 */
public class OrderCancelEvent extends Event<Exchange> {
    public final String id;
    public final boolean canceled;

    public OrderCancelEvent(String id, Exchange source, boolean canceled) {
        super(source);
        this.id = id;
        this.canceled = canceled;
    }
}
