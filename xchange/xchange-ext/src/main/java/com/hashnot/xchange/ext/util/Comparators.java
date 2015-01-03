package com.hashnot.xchange.ext.util;

import java.util.Comparator;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
final public class Comparators {
    /**
    * @author Rafał Krupiński
    */
    public static class EntryValueComparator<K, V extends Comparable<V>> implements Comparator<Map.Entry<K, V>> {
        @Override
        public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
            return o1.getValue().compareTo(o2.getValue());
        }
    }

    /**
    * @author Rafał Krupiński
    */
    public static class MultiplyingComparator<T> implements Comparator<T> {
        private final Comparator<T> comparator;
        final private int multiplicand;

        public MultiplyingComparator(Comparator<T> comparator, int multiplicand) {
            this.comparator = comparator;
            this.multiplicand = multiplicand;
        }

        @Override
        public int compare(T o1, T o2) {
            return comparator.compare(o1, o2) * multiplicand;
        }
    }

    private Comparators() {
    }
}
