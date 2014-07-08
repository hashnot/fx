package com.hashnot.fx.util;

import com.hashnot.fx.spi.ext.IFeeService;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.io.IOException;
import java.math.BigDecimal;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;

/**
 * @author Rafał Krupiński
 */
public class FeeHelper {
    public static BigDecimal getFeePercent(IFeeService feeService, LimitOrder order) {
        BigDecimal result = feeService.getFeePercent(order.getCurrencyPair());
        if (order.getType() == ASK)
            result = result.negate();
        return result;
    }

    public static BigDecimal addPercent(BigDecimal inValue, BigDecimal percent) {
        return inValue.multiply(BigDecimal.ONE.add(percent));
    }
}
