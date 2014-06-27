package com.hashnot.fx.spi;

import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.util.Comparator;

/**
 * @author Rafał Krupiński
 */
public class LimitOrderComparator implements Comparator<LimitOrder> {
    public static final Comparator<LimitOrder> ORDER_COMPARATOR = new LimitOrderComparator();

    @Override
    public int compare(LimitOrder o1, LimitOrder o2) {
        Order.OrderType type = o1.getType();
        if (type != o2.getType())
            throw new IllegalArgumentException("Mismatching OrderType");
        int diff = o1.getLimitPrice().compareTo(o2.getLimitPrice());
        return type == Order.OrderType.ASK ? diff : -diff;
    }
}
