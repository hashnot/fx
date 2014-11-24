package com.hashnot.fx.framework;

import com.hashnot.xchange.ext.event.Event;

import java.math.BigDecimal;

/**
 * @author Rafał Krupiński
 */
public class BestOfferEvent extends Event<MarketSide> {
    public final BigDecimal price;

    public BestOfferEvent(BigDecimal price, MarketSide source) {
        super(source);

        this.price = price;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o != null && o instanceof BestOfferEvent && equals((BestOfferEvent) o);
    }

    protected boolean equals(BestOfferEvent o) {
        return source.equals(o.source) && price == null ? o.price == null : price.equals(o.price);
    }

    @Override
    public int hashCode() {
        int result = source.hashCode();
        result = 31 * result + (price != null ? price.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return price + " @" + source;
    }
}
