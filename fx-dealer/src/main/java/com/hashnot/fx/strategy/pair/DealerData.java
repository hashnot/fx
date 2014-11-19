package com.hashnot.fx.strategy.pair;

import com.xeiam.xchange.Exchange;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Structure holding state of the dealer state machine
 *
 * @author Rafał Krupiński
 */
public class DealerData {
    // side -> map(exchange -> best offer price)
    final private Map<Exchange, BigDecimal> bestOffers = new HashMap<>();

    private Exchange closeExchange;

    private Exchange openExchange;

    public DealerData() {
    }

    public DealerData(Exchange openExchange, Exchange closeExchange, Map<Exchange, BigDecimal> bestOffers) {
        this.closeExchange = closeExchange;
        this.openExchange = openExchange;
        this.bestOffers.putAll(bestOffers);
    }

    public Map<Exchange, BigDecimal> getBestOffers() {
        return bestOffers;
    }

    public Exchange getCloseExchange() {
        return closeExchange;
    }

    public void setCloseExchange(Exchange closeExchange) {
        this.closeExchange = closeExchange;
    }

    public Exchange getOpenExchange() {
        return openExchange;
    }

    public void setOpenExchange(Exchange openExchange) {
        this.openExchange = openExchange;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof DealerData && equals((DealerData) o);
    }

    protected boolean equals(DealerData that) {
        return bestOffers.equals(that.bestOffers)
                && (closeExchange == null ? that.closeExchange == null : closeExchange.equals(that.closeExchange))
                && (openExchange == null ? that.openExchange == null : openExchange.equals(that.openExchange));
    }

    @Override
    public int hashCode() {
        int result = bestOffers.hashCode();
        result = 31 * result + (closeExchange != null ? closeExchange.hashCode() : 0);
        result = 31 * result + (openExchange != null ? openExchange.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DealerData{" +
                "bestOffers=" + bestOffers +
                ", closeExchange=" + closeExchange +
                ", openExchange=" + openExchange +
                '}';
    }
}
