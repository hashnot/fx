package com.hashnot.fx.util;

import java.util.Arrays;
import java.util.List;

/**
 * @author Rafał Krupiński
 */
public class CurrencyPairUtil {
    public static final String GBP = "GBP";
    public static final String EUR = "EUR";
    public static final String USD = "USD";
    public static final String CHF = "CHF";
    private static List<String> baseCurrencies = Arrays.asList(EUR, GBP, "AUD", "NZD", USD, "CAD", CHF, "JPY");

    public static boolean isNonReversed(String base, String quoted) {
        assert !base.equals(quoted) : "Base and quoted are equal";
        int baseIdx = baseCurrencies.indexOf(base);
        if (baseIdx < 0) baseIdx = Integer.MAX_VALUE;

        int quotIDx = baseCurrencies.indexOf(quoted);
        if (quotIDx < 0) quotIDx = Integer.MAX_VALUE;

        if (baseIdx == quotIDx)
            throw new IllegalArgumentException("Neither is a base currency");

        return baseIdx < quotIDx;

    }
}
