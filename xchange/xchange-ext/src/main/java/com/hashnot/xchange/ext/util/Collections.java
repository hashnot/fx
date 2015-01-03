package com.hashnot.xchange.ext.util;

import java.util.Comparator;
import java.util.Iterator;

/**
 * @author Rafał Krupiński
 */
final public class Collections {
    private Collections() {
    }

    public static <V> MinMax<V> minMax(Iterable<V> collection, Comparator<V> comparator) {
        V min;
        V max;

        Iterator<V> iterator = collection.iterator();
        if (iterator.hasNext()) {
            min = iterator.next();
            max = min;
        } else {
            min = null;
            max = null;
        }

        while (iterator.hasNext()) {
            V v = iterator.next();
            if (comparator.compare(v, min) < 0)
                min = v;
            if (comparator.compare(v, max) > 0)
                max = v;
        }
        return new MinMax<>(min, max);
    }

    public static class MinMax<V> {
        final public V min;

        final public V max;

        MinMax(V min, V max) {
            this.min = min;
            this.max = max;
        }

    }
}
