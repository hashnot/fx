package com.hashnot.fx.util;

import com.xeiam.xchange.dto.trade.LimitOrder;

/**
 * @author Rafał Krupiński
 */
public class Orders {
    public static boolean equals(LimitOrder o1, LimitOrder o2) {
        return o1.getLimitPrice().equals(o2.getLimitPrice()) && o1.getTradableAmount().equals(o2.getTradableAmount());
    }
}
