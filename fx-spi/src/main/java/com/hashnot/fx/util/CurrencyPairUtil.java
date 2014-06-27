package com.hashnot.fx.util;

import com.xeiam.xchange.currency.CurrencyPair;

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

    public static boolean isNonReversed(CurrencyPair cp) {
        return isNonReversed(cp.baseSymbol, cp.counterSymbol);
    }

    public static boolean isNonReversed(String base, String counter) {
        assert !base.equals(counter) : "Base and counter are equal";
        int baseIdx = baseCurrencies.indexOf(base);
        if (baseIdx < 0) baseIdx = Integer.MAX_VALUE;

        int quotIDx = baseCurrencies.indexOf(counter);
        if (quotIDx < 0) quotIDx = Integer.MAX_VALUE;

        if (baseIdx == quotIDx)
            throw new IllegalArgumentException("Neither is a base currency");

        return baseIdx < quotIDx;

    }

    public static CurrencyPair reverse(CurrencyPair cp) {
        return new CurrencyPair(cp.counterSymbol, cp.baseSymbol);
    }
}
