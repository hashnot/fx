package com.hashnot.fx;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;

/**
 * @author Rafał Krupiński
 */
public class OrderBookUpdateEvent {

    private Supplier<OrderBook> changes = Suppliers.memoize(this::diff);

    public final CurrencyPair pair;

    public final OrderBook before;
    public final OrderBook after;

    public OrderBookUpdateEvent(CurrencyPair pair, OrderBook before, OrderBook after) {
        this.pair = pair;
        this.before = before;
        this.after = after;
    }

    public OrderBook getChanges() {
        return changes.get();
    }

    protected OrderBook diff() {
        // TODO implement
        throw new IllegalStateException("not implemented");
    }
}
