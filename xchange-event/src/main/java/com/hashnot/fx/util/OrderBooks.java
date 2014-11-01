package com.hashnot.fx.util;

import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import static com.hashnot.fx.util.Numbers.BigDecimal.isZero;
import static com.hashnot.fx.util.Orders.Price.compareTo;
import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;

/**
 * @author Rafał Krupiński
 */
public class OrderBooks {

    public static OrderBook removeOverLimit(OrderBook orderBook, BigDecimal baseLimit, BigDecimal counterLimit) {
        List<LimitOrder> asks = removeOverIndex(orderBook.getAsks(), amountIndex(orderBook.getAsks(), baseLimit, counterLimit));
        List<LimitOrder> bids = removeOverIndex(orderBook.getBids(), amountIndex(orderBook.getBids(), baseLimit, counterLimit));
        return new OrderBook(orderBook.getTimeStamp(), asks, bids);
    }

    private static List<LimitOrder> removeOverIndex(List<LimitOrder> orders, int limitIndex) {
        return orders.subList(0, limitIndex);
    }

    public static boolean equals(OrderBook o1, OrderBook o2) {
        return equals(o1.getAsks(), o2.getAsks()) && equals(o1.getBids(), o2.getBids());
    }

    public static boolean equals(List<LimitOrder> l1, List<LimitOrder> l2) {
        ListIterator<LimitOrder> e1 = l1.listIterator();
        ListIterator<LimitOrder> e2 = l2.listIterator();
        while (e1.hasNext() && e2.hasNext()) {
            LimitOrder o1 = e1.next();
            LimitOrder o2 = e2.next();
            if (!(o1 == null ? o2 == null : Orders.equals(o1, o2)))
                return false;
        }
        return !(e1.hasNext() || e2.hasNext());
    }

    private static int amountIndex(List<LimitOrder> orders, BigDecimal baseLimit, BigDecimal counterLimit) {
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        Iterator<LimitOrder> oi = orders.iterator();
        int i = 0;
        while (oi.hasNext() && (totalValue.compareTo(counterLimit) <= 0 || totalAmount.compareTo(baseLimit) <= 0)) {
            ++i;

            LimitOrder order = oi.next();
            totalAmount = totalAmount.add(order.getTradableAmount());
            totalValue = totalValue.add(order.getTradableAmount().multiply(order.getLimitPrice(), Orders.c));
        }
        return i;
    }

    public static void updateNetPrices(OrderBook orderBook, BigDecimal fee) {
        updateNetPrices(orderBook, fee, Order.OrderType.ASK);
        updateNetPrices(orderBook, fee, Order.OrderType.BID);
    }

    private static void updateNetPrices(OrderBook orderBook, BigDecimal fee, Order.OrderType type) {
        List<LimitOrder> orders = orderBook.getOrders(type);
        for (LimitOrder order : orders) {
            order.setNetPrice(Orders.getNetPrice(order, fee));
        }
    }

    public static OrderBook diff(OrderBook before, OrderBook after) {
        if (before == null)
            return after;

        List<LimitOrder> asks = diff(before, after, Order.OrderType.ASK);
        List<LimitOrder> bids = diff(before, after, Order.OrderType.BID);
        return new OrderBook(after.getTimeStamp(), asks, bids);
    }

    private static List<LimitOrder> diff(OrderBook beforeBook, OrderBook afterBook, Order.OrderType type) {
        List<LimitOrder> beforeOrders = beforeBook.getOrders(type);
        List<LimitOrder> newOrders = afterBook.getOrders(type);

        return diff(beforeOrders, newOrders, type);
    }

    public static List<LimitOrder> diff(List<LimitOrder> oldOrders, List<LimitOrder> newOrders, Order.OrderType type) {
        List<LimitOrder> result = new LinkedList<>();

        Iterator<LimitOrder> beforeIter = oldOrders.iterator();
        Iterator<LimitOrder> afterIter = newOrders.iterator();

        LimitOrder before = next(beforeIter);
        LimitOrder after = next(afterIter);

        while (before != null || after != null) {
            // if there are orders with same price, add the (price) difference, if non-zero
            // from before add with negative amount
            // from after add with positive amount

            // simulate diff result if any or both orders are null
            int diff;
            if (after != null && before != null)
                diff = compareTo(after.getLimitPrice(), before.getLimitPrice(), type);
            else if (after != null)
                diff = -1;
            else
                diff = 1;

            if (diff == 0) {
                BigDecimal amountDiff = after.getTradableAmount().subtract(before.getTradableAmount());

                if (!isZero(amountDiff))
                    result.add(from(before).tradableAmount(amountDiff).build());

                before = next(beforeIter);
                after = next(afterIter);
            } else if (diff < 1) {
                result.add(after);
                after = next(afterIter);
            } else {
                assert before != null;
                result.add(from(before).tradableAmount(before.getLimitPrice().negate()).build());
                before = next(beforeIter);
            }
        }

        return result;
    }

    private static <T> T next(Iterator<T> iter) {
        return iter.hasNext() ? iter.next() : null;
    }
}
