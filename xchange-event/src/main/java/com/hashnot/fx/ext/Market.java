package com.hashnot.fx.ext;

import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.currency.CurrencyPair;

/**
 * @author Rafał Krupiński
 */
public class Market {
    public final IExchange exchange;
    public final CurrencyPair listing;

    public Market(IExchange exchange, CurrencyPair listing) {
        this.exchange = exchange;
        this.listing = listing;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o != null && o instanceof Market && equals((Market) o);
    }

    protected boolean equals(Market market) {
        return exchange.equals(market.exchange) && listing.equals(market.listing);
    }

    @Override
    public int hashCode() {
        int result = exchange.hashCode();
        result = 31 * result + listing.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return listing + "@" + exchange;
    }
}
