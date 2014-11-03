package com.hashnot.fx.framework;

import com.hashnot.xchange.ext.Market;
import com.xeiam.xchange.dto.Order;

/**
* @author Rafał Krupiński
*/
public class MarketSide {

    public final Market market;
    public final Order.OrderType side;

    public MarketSide(Market market, Order.OrderType side) {
        this.side = side;
        this.market = market;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o != null && o instanceof MarketSide && equals((MarketSide) o);
    }

    protected boolean equals(MarketSide that) {
        return market.equals(that.market) && side == that.side;
    }

    @Override
    public int hashCode() {
        int result = market.hashCode();
        result = 31 * result + side.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return side.name() + '/' + market.toString();
    }
}
