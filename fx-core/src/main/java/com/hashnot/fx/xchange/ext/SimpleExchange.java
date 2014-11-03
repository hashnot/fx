package com.hashnot.fx.xchange.ext;

import com.hashnot.xchange.ext.AbstractExchange;
import com.xeiam.xchange.Exchange;

/**
 * @author Rafał Krupiński
 */
public class SimpleExchange extends AbstractExchange {

    private final Exchange exchange;

    public SimpleExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    protected Exchange getExchange() {
        return exchange;
    }
}
