package com.hashnot.fx;

import com.xeiam.xchange.dto.trade.LimitOrder;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * @author Rafał Krupiński
 */
public class FeeHelper {
    public static BigDecimal getFeePercent(IFeeService feeService, LimitOrder order) throws IOException {
        return feeService.getFeePercent(order.getCurrencyPair());
    }

    public static BigDecimal addPercent(BigDecimal inValue, BigDecimal percent) {
        return inValue.multiply(BigDecimal.ONE.add(percent));
    }

    public static BigDecimal subtractPercent(BigDecimal inValue, BigDecimal percent) {
        return addPercent(inValue, percent.negate());
    }
}
