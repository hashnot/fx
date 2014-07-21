package com.hashnot.fx.util;

import com.hashnot.fx.spi.ext.IFeeService;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;

/**
 * @author Rafał Krupiński
 */
public class FeeHelper {
    public static BigDecimal getFeePercent(IFeeService feeService, LimitOrder order) {
        CurrencyPair pair = order.getCurrencyPair();
        Order.OrderType type = order.getType();
        return getFeePercent(pair, type, feeService);
    }

    public static BigDecimal getFeePercent(CurrencyPair pair, Order.OrderType type, IFeeService feeService) {
        BigDecimal result = feeService.getFeePercent(pair);
        if (type == ASK)
            result = result.negate();
        return result;
    }

    public static BigDecimal addPercent(BigDecimal inValue, BigDecimal percent) {
        return inValue.multiply(BigDecimal.ONE.add(percent), Orders.c);
    }
}
