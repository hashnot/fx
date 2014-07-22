package com.hashnot.fx.util;

/**
 * @author Rafał Krupiński
 */
public class Numbers {
    public static <T extends Comparable<T>> T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static <T extends Comparable<T>> T min(T a, T b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static <T extends Comparable<T>> boolean equals(T o1, T o2) {
        return o1.compareTo(o2) == 0;
    }
}
