package com.hashnot.fx.util;

import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

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

    private static boolean equals(List<LimitOrder> l1, List<LimitOrder> l2) {
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
}
