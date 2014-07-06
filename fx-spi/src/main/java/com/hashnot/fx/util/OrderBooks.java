package com.hashnot.fx.util;

import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;

/**
 * @author Rafał Krupiński
 */
public class OrderBooks {
    public static boolean limitedEquals(OrderBook o1, OrderBook o2, BigDecimal baseLimit, BigDecimal counterLimit) {
        return limitedEquals(o1.getAsks(), o2.getAsks(), counterLimit, Order.OrderType.ASK)
                && limitedEquals(o1.getBids(), o2.getBids(), baseLimit, Order.OrderType.BID);
    }

    private static boolean limitedEquals(List<LimitOrder> l1, List<LimitOrder> l2, BigDecimal limit, Order.OrderType type) {
        BigDecimal total = BigDecimal.ZERO;
        Iterator<LimitOrder> i1 = l1.iterator();
        Iterator<LimitOrder> i2 = l2.iterator();
        while (i1.hasNext() && i2.hasNext() && total.compareTo(limit) <= 0) {
            LimitOrder o1 = i1.next();
            LimitOrder o2 = i2.next();
            if (!Orders.equals(o1, o2))
                return false;
            BigDecimal amount = o1.getTradableAmount();
            if (type == Order.OrderType.BID)
                amount = amount.multiply(o1.getLimitPrice());
            total = total.add(amount);
        }
        return true;
    }

    public static List<LimitOrder> get(OrderBook orderBook, Order.OrderType dir) {
        return dir == Order.OrderType.ASK ? orderBook.getAsks() : orderBook.getBids();
    }
}
