package com.hashnot.xchange.event;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.OpenOrders;

/**
 * @author Rafał Krupiński
 */
public class OpenOrdersEvent extends Event<Exchange> {
    public final OpenOrders openOrders;

    public OpenOrdersEvent(Exchange source, OpenOrders openOrders) {
        super(source);
        this.openOrders = openOrders;
    }
}
