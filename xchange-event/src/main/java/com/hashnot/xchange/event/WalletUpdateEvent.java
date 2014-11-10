package com.hashnot.xchange.event;

import com.xeiam.xchange.Exchange;

import java.math.BigDecimal;

/**
 * @author Rafał Krupiński
 */
public class WalletUpdateEvent extends Event<Exchange> {
    public final String currency;
    public final BigDecimal amount;

    public WalletUpdateEvent(BigDecimal amount, String currency, Exchange source) {
        super(source);
        this.amount = amount;
        this.currency = currency;
    }
}
