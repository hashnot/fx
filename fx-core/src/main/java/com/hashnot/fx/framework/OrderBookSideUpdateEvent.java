package com.hashnot.fx.framework;

import com.hashnot.fx.util.OrderBooks;
import com.hashnot.xchange.event.Event;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.util.List;
import java.util.function.Supplier;

/**
 * @author Rafał Krupiński
 */
public class OrderBookSideUpdateEvent extends Event<MarketSide> {
    public final List<LimitOrder> oldOrders;
    public final List<LimitOrder> newOrders;
    final private Supplier<List<LimitOrder>> changes;

    public OrderBookSideUpdateEvent(MarketSide source, List<LimitOrder> oldOrders, List<LimitOrder> newOrders) {
        super(source);
        this.oldOrders = oldOrders;
        this.newOrders = newOrders;
        changes = () -> OrderBooks.diff(this.oldOrders, this.newOrders, source.side);
    }

    public List<LimitOrder> getChanges() {
        return changes.get();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof OrderBookSideUpdateEvent && equals((OrderBookSideUpdateEvent) o);
    }

    protected boolean equals(OrderBookSideUpdateEvent that) {
        return source.equals(that.source) && newOrders.equals(that.newOrders) && !(oldOrders != null ? !oldOrders.equals(that.oldOrders) : that.oldOrders != null);

    }

    @Override
    public int hashCode() {
        int result = oldOrders != null ? oldOrders.hashCode() : 0;
        result = 31 * result + newOrders.hashCode();
        result = 31 * result + source.hashCode();
        return result;
    }
}
