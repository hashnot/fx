package com.hashnot.xchange.ext.util;

import com.xeiam.xchange.dto.Order;

import java.math.BigInteger;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public final class Numbers {
    /**
     * Allows parameters to be null
     */
    public static <T extends Comparable<T>> boolean eq(T o1, T o2) {
        return o1 == o2 || o1 != null && o2 != null && o1.compareTo(o2) == 0;
    }

    public static <T extends Comparable<T>> boolean lt(T o1, T o2) {
        return o1.compareTo(o2) < 0;
    }

    public static <T extends Comparable<T>> boolean gt(T o1, T o2) {
        return o1.compareTo(o2) > 0;
    }

    public static final class BigDecimal {
        // used in tests
        public static final java.math.BigDecimal _ONE = ONE.negate();

        // common constant
        public static final java.math.BigDecimal TWO = new java.math.BigDecimal(2);

        public static boolean isZero(java.math.BigDecimal number) {
            return eq(number, ZERO);
        }

        private BigDecimal() {
        }
    }

    private Numbers() {
    }

    public final static class Price {
        /**
         * Conventional MAX_VALUE
         */
        final public static java.math.BigDecimal MAX = new java.math.BigDecimal(new BigInteger(new byte[]{1}), -Integer.MAX_VALUE);

        /**
         * Conventional MIN_VALUE
         */
        final public static java.math.BigDecimal MIN = new java.math.BigDecimal(new BigInteger(-1, new byte[]{1}), -Integer.MAX_VALUE);

        /**
         * @see java.lang.Comparable#compareTo(Object)
         */
        public static <T extends Comparable<T>> int compareTo(T n1, T n2, Order.OrderType side) {
            return n1.compareTo(n2) * Orders.factor(side);
        }

        /**
         * true if first would be further to the average then the second in an order book.
         */
        public static <T extends Comparable<T>> boolean isFurther(T better, T worse, Order.OrderType side) {
            return compareTo(better, worse, side) > 0;
        }

        /**
         * true if first would be closer to the average then the second in an order book.
         */
        public static <T extends Comparable<T>> boolean isCloser(T worse, T better, Order.OrderType side) {
            return compareTo(worse, better, side) < 0;
        }

        public static java.math.BigDecimal forNull(Order.OrderType type) {
            return type == ASK ? MAX : MIN;
        }

        private Price() {
        }
    }
}
