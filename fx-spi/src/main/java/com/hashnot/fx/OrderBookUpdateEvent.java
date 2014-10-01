package com.hashnot.fx;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static com.hashnot.fx.util.Numbers.BigDecimal.isZero;

/**
 * @author Rafał Krupiński
 */
public class OrderBookUpdateEvent {
    public final IExchange source;

    private final Supplier<OrderBook> changes = Suppliers.memoize(this::diff);

    public final CurrencyPair pair;

    public final OrderBook before;
    public final OrderBook after;

    public OrderBookUpdateEvent(IExchange source, CurrencyPair pair, OrderBook before, OrderBook after) {
        this.source = source;
        this.pair = pair;
        this.before = before;
        this.after = after;
    }

    public OrderBook getChanges() {
        return changes.get();
    }

    protected OrderBook diff() {
        if (before == null)
            return after;

        List<LimitOrder> asks = diff(before, after, Order.OrderType.ASK);
        List<LimitOrder> bids = diff(before, after, Order.OrderType.BID);
        return new OrderBook(after.getTimeStamp(), asks, bids);
    }

    private List<LimitOrder> diff(OrderBook beforeBook, OrderBook afterBook, Order.OrderType type) {
        List<LimitOrder> result = new LinkedList<>();

        List<LimitOrder> beforeOrders = beforeBook.getOrders(type);
        List<LimitOrder> afterOrders = afterBook.getOrders(type);

        Iterator<LimitOrder> beforeIter = beforeOrders.iterator();
        Iterator<LimitOrder> afterIter = afterOrders.iterator();

        LimitOrder before = next(beforeIter);
        LimitOrder after = next(afterIter);

        while (before != null || after != null) {
            // if there are orders with same price, add the (price) difference, if non-zero
            // from before add with negative amount
            // from after add with positive amount

            // simulate diff result if any or both orders are null
            int diff;
            if (after != null && before != null)
                diff = after.getLimitPrice().compareTo(before.getLimitPrice());
            else if (after != null)
                diff = -1;
            else
                diff = 1;

            if (diff == 0) {
                BigDecimal amountDiff = after.getTradableAmount().subtract(before.getTradableAmount());

                if (!isZero(amountDiff))
                    result.add(Orders.withAmount(before, amountDiff));

                before = next(beforeIter);
                after = next(afterIter);
            } else if (diff < 1) {
                result.add(after);
                after = next(afterIter);
            } else {
                assert before != null;
                result.add(Orders.withAmount(before, before.getLimitPrice().negate()));
                before = next(beforeIter);
            }
        }

        return result;
    }

    private static <T> T next(Iterator<T> iter) {
        return iter.hasNext() ? iter.next() : null;
    }
}
