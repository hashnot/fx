package com.hashnot.fx.ext;

import java.math.BigDecimal;

import static com.xeiam.xchange.dto.Order.OrderType;

/**
 * @author Rafał Krupiński
 */
public class BestOfferEvent extends Event<Market> {
    public final BigDecimal price;
    public final OrderType type;

    public BestOfferEvent(BigDecimal price, OrderType type, Market source) {
        super(source);

        assert type != null : "null type";

        this.price = price;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        BestOfferEvent that = (BestOfferEvent) o;

        if (price != null ? !price.equals(that.price) : that.price != null) return false;
        if (type != that.type) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (price != null ? price.hashCode() : 0);
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return type + " " + price + " @" + source;
    }
}
