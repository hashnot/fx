package com.hashnot.fx.util;

import com.google.common.collect.SetMultimap;

import java.util.Collections;

/**
 * @author Rafał Krupiński
 */
final public class Multimaps {

    private static final SetMultimap<?, ?> EMPTY_MULTIMAP = com.google.common.collect.Multimaps.newSetMultimap(Collections.emptyMap(), Collections::emptySet);

    @SuppressWarnings("unchecked")
    public static <K, V> SetMultimap<K, V> emptyMultimap() {
        return (SetMultimap<K, V>) EMPTY_MULTIMAP;
    }

}
