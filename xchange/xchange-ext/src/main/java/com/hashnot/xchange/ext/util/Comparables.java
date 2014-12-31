package com.hashnot.xchange.ext.util;

/**
 * @author Rafał Krupiński
 */
public final class Comparables {
    /**
     * Allows parameters to be null
     */
    public static <T extends Comparable<T>> boolean eq(T o1, T o2) {
        return o1 == o2 || o1 != null && o2 != null && o1.compareTo(o2) == 0;
    }

    public static <T extends Comparable<T>> boolean lt(T o1, T o2) {
        return o1.compareTo(o2) < 0;
    }

    public static <T extends Comparable<T>> boolean lte(T o1, T o2) {
        return o1.compareTo(o2) >= 0;
    }

    public static <T extends Comparable<T>> boolean gt(T o1, T o2) {
        return o1.compareTo(o2) > 0;
    }

    /**
     * @return o1 >= o2
     */
    public static <T extends Comparable<T>> boolean gte(T o1, T o2) {
        return o1.compareTo(o2) >= 0;
    }

    private Comparables() {
    }

}
