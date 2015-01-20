package com.hashnot.xchange.ext.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Comparator;

import static org.junit.Assert.assertEquals;

public class CollectionsTest {

    @Test
    public void testMinMax() throws Exception {
        Collections.MinMax<Integer> minMax = Collections.minMax(Arrays.asList(0, 1, 2), new ComparableComparator<>());
        assertEquals(0, (int) minMax.min);
        assertEquals(2, (int) minMax.max);
    }

    @Test
    public void testMinMaxReversed() throws Exception {
        Collections.MinMax<Integer> minMax = Collections.minMax(Arrays.asList(2, 1, 0), new ComparableComparator<>());
        assertEquals(0, (int) minMax.min);
        assertEquals(2, (int) minMax.max);
    }

    final static class ComparableComparator<T extends Comparable<T>> implements Comparator<T> {
        @Override
        public int compare(T o1, T o2) {
            return o1.compareTo(o2);
        }
    }
}
