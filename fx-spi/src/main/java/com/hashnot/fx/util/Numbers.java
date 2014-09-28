package com.hashnot.fx.util;

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

    public static <T extends Comparable<T>> boolean isEqual(T o1, T o2) {
        return o1.compareTo(o2) == 0;
    }

    public static <T extends Comparable<T>> boolean lt(T o1, T o2) {
        return o1.compareTo(o2) < 0;
    }

    public static final class BigDecimal {
        public static final java.math.BigDecimal _ONE = java.math.BigDecimal.ONE.negate();

        public static boolean isZero(java.math.BigDecimal number){
            return isEqual(number, java.math.BigDecimal.ZERO);
        }

        private BigDecimal() {
        }
    }

    private Numbers() {
    }
}
