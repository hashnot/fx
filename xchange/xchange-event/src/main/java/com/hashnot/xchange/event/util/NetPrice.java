package com.hashnot.xchange.event.util;

import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.dto.trade.LimitOrder;

/**
 * @author Rafał Krupiński
 */
public class NetPrice {
    public static LimitOrder closing(LimitOrder order, IExchangeMonitor feeService) {
        LimitOrder result = Orders.closing(order);
        updateNetPrice(result, feeService);
        return result;
    }

    public static void updateNetPrice(LimitOrder order, IExchangeMonitor x) {
        order.setNetPrice(Orders.getNetPrice(order, x.getMarketMetadata(order.getCurrencyPair()).getOrderFeeFactor()));
    }
}
