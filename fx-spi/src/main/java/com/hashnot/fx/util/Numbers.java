package com.hashnot.fx.util;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public final class Numbers {
    public static <T extends Comparable<T>> T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static <T extends Comparable<T>> T min(T a, T b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    /**
     * Allows parameters to be null
     */
    public static <T extends Comparable<T>> boolean eq(T o1, T o2) {
        return o1 == o2 || o1 != null && o1.compareTo(o2) == 0;
    }

    public static <T extends Comparable<T>> boolean lt(T o1, T o2) {
        return o1.compareTo(o2) < 0;
    }

    public static final class BigDecimal {
        public static final java.math.BigDecimal _ONE = ONE.negate();

        public static boolean isZero(java.math.BigDecimal number) {
            return eq(number, ZERO);
        }

        private BigDecimal() {
        }
    }

    private Numbers() {
    }
}
