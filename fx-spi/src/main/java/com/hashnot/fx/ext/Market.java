package com.hashnot.fx.ext;

import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.currency.CurrencyPair;

/**
 * @author Rafał Krupiński
 */
public final class Market {
    public final IExchange exchange;
    public final CurrencyPair listing;

    public Market(IExchange exchange, CurrencyPair listing) {
        this.exchange = exchange;
        this.listing = listing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Market market = (Market) o;

        if (!exchange.equals(market.exchange)) return false;
        if (!listing.equals(market.listing)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = exchange.hashCode();
        result = 31 * result + listing.hashCode();
        return result;
    }
}
