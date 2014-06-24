package com.hashnot.fx.spi;

import java.math.BigDecimal;

/**
 * @author Rafał Krupiński
 */
public class Offer {
    final public BigDecimal amount;
    final public BigDecimal price;
    final public Dir dir;

    /**
     * currency being quoted
     */
    final public String base;

    /**
     * currency the value is represented in
     */
    final public String counter;
    final public IMarket market;

    public Offer(BigDecimal amount, BigDecimal price, Dir dir, String base, String counter, IMarket market) {
        this.amount = amount;
        this.price = price;
        this.dir = dir;
        this.base = base;
        this.counter = counter;
        this.market = market;
    }

    @Override
    public String toString() {
        return base + counter + " " + dir + " " + price + " x " + amount;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof Offer && equals((Offer) obj));
    }

    private boolean equals(Offer o) {
        return amount.equals(o.amount) &&
                price.equals(o.price) &&
                dir == o.dir &&
                base.equals(o.base) &&
                counter.equals(o.counter) &&
                (market == null ? o.market == null : market.equals(o.market));
    }

    @Override
    public int hashCode() {
        int hash = amount.hashCode();
        hash = 31 * hash + price.hashCode();
        hash = 31 * hash + dir.hashCode();
        hash = 31 * hash + base.hashCode();
        hash = 31 * hash + counter.hashCode();
        return 31 * hash + market.hashCode();
    }
}
