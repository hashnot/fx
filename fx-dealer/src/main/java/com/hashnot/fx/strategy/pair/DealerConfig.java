package com.hashnot.fx.strategy.pair;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;

import static com.hashnot.xchange.ext.util.Orders.revert;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;

/**
 * @author Rafał Krupiński
 */
public class DealerConfig {
    final public Order.OrderType side;

    final public CurrencyPair listing;

    public DealerConfig(Order.OrderType side, CurrencyPair listing) {
        this.side = side;
        this.listing = listing;
    }

    public String outgoingCurrency() {
        return incomingCurrency(revert(side), listing);
    }

    public String incomingCurrency() {
        return incomingCurrency(side, listing);
    }

    public static String incomingCurrency(Order.OrderType side, CurrencyPair listing) {
        return side == ASK ? listing.counterSymbol : listing.baseSymbol;
    }

    @Override
    public String toString() {
        return side + "@" + listing;
    }

    public boolean is(Order.OrderType side) {
        return this.side == side;
    }
}
