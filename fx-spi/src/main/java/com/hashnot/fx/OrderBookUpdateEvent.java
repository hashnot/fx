package com.hashnot.fx;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.hashnot.fx.util.Numbers;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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

        LimitOrder before = beforeIter.next();
        LimitOrder after = afterIter.next();

        while (beforeIter.hasNext() || afterIter.hasNext()) {
            // if there are orders with same price, add the (price) difference, if non-zero
            // from before add with negative amount
            // from after add with positive amount

            int diff = after.getLimitPrice().compareTo(before.getLimitPrice());
            if (diff == 0) {
                BigDecimal amountDiff = after.getTradableAmount().subtract(before.getTradableAmount());

                if (!Numbers.equals(amountDiff, BigDecimal.ZERO))
                    result.add(Orders.withAmount(before, amountDiff));

                if (beforeIter.hasNext()) before = beforeIter.next();
                if (afterIter.hasNext()) after = afterIter.next();
            } else if (diff < 1) {
                result.add(after);
                if (afterIter.hasNext()) after = afterIter.next();
            } else {
                result.add(Orders.withPrice(before, before.getLimitPrice().negate()));
                if (beforeIter.hasNext()) before = beforeIter.next();
            }
        }

        return result;
    }
}
