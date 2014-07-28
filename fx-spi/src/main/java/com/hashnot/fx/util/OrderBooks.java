package com.hashnot.fx.util;

import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.*;

/**
 * @author Rafał Krupiński
 */
public class OrderBooks {

    public static void removeOverLimit(OrderBook orderBook, BigDecimal baseLimit, BigDecimal counterLimit) {
        int limit = amountIndex(orderBook, baseLimit, counterLimit);
        removeOverIndex(orderBook.getAsks(), limit);
        removeOverIndex(orderBook.getBids(), limit);
    }

    private static void removeOverIndex(List<LimitOrder> orders, int limitIndex) {
        for (int i = orders.size() - 1; i > limitIndex; i--)
            orders.remove(i);
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


    public static List<LimitOrder> get(OrderBook orderBook, Order.OrderType dir) {
        assert orderBook != null : "null orderBook";
        assert dir != null : "null orderType";
        return dir == Order.OrderType.ASK ? orderBook.getAsks() : orderBook.getBids();
    }

    private static int amountIndex(OrderBook orderBook, BigDecimal baseLimit, BigDecimal counterLimit) {
        BigDecimal totalAsk = BigDecimal.ZERO;
        BigDecimal totalBid = BigDecimal.ZERO;

        Iterator<LimitOrder> ai = orderBook.getAsks().iterator();
        Iterator<LimitOrder> bi = orderBook.getBids().iterator();
        int i = -1;
        while ((ai.hasNext() || bi.hasNext()) && (totalAsk.compareTo(counterLimit) <= 0 || totalBid.compareTo(baseLimit) <= 0)) {
            ++i;

            if (ai.hasNext()) {
                LimitOrder order = ai.next();
                totalAsk = totalAsk.add(order.getTradableAmount().multiply(order.getLimitPrice(), Orders.c));
            }

            if (bi.hasNext()) {
                LimitOrder order = bi.next();
                totalBid = totalBid.add(order.getTradableAmount());
            }
        }
        return i;
    }

    public static void updateNetPrices(IExchange exchange, CurrencyPair pair) {
        BigDecimal fee = exchange.getFeePercent(pair);
        OrderBook orderBook = exchange.getOrderBook(pair);
        updateNetPrices(orderBook, fee, Order.OrderType.ASK);
        updateNetPrices(orderBook, fee, Order.OrderType.BID);
    }

    private static void updateNetPrices(OrderBook orderBook, BigDecimal fee, Order.OrderType type) {
        List<LimitOrder> orders = get(orderBook, type);
        for (LimitOrder order : orders) {
            order.setNetPrice(Orders.getNetPrice(order, fee));
        }
    }
}
