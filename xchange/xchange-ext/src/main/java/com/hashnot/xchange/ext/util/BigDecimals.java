package com.hashnot.xchange.ext.util;

import java.math.BigDecimal;

/**
 * @author Rafał Krupiński
 */
public final class BigDecimals {
    // used in tests
    public static final BigDecimal _ONE = BigDecimal.ONE.negate();

    // common constant
    public static final BigDecimal TWO = new BigDecimal(2);

    public static boolean isZero(BigDecimal number) {
        return Comparables.eq(number, BigDecimal.ZERO);
    }

    private BigDecimals() {
    }
}
