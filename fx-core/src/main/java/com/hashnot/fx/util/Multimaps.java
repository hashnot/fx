package com.hashnot.fx.util;

import com.google.common.collect.Multimap;

import java.util.Collections;

/**
 * @author Rafał Krupiński
 */
final public class Multimaps {

    private static final Multimap<?, ?> EMPTY_MULTIMAP = com.google.common.collect.Multimaps.newSetMultimap(Collections.emptyMap(), Collections::emptySet);

    @SuppressWarnings("unchecked")
    public static <K, V> Multimap<K, V> emptyMultimap() {
        return (Multimap<K, V>) EMPTY_MULTIMAP;
    }

}
