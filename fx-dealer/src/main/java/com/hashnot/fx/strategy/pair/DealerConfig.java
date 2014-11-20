package com.hashnot.fx.strategy.pair;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;

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
}
