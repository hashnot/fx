package com.hashnot.xchange.ext.util;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class Maps {

    public static <K, V> Map<K, V> map(K key, V value) {
        HashMap<K, V> result = new HashMap<>();
        result.put(key, value);
        return result;
    }

    @SafeVarargs
    public static <K> MapMaker<K> keys(K... keys) {
        return new MapMaker<>(keys);
    }

    public static final class MapMaker<K> {
        private K[] keys;

        protected MapMaker(K[] keys) {
            this.keys = keys;
        }

        @SafeVarargs
        public final <V> Map<K, V> map(V... values) {
            assert keys.length == values.length;
            Map<K, V> result = new HashMap<>(keys.length);
            for (int i = 0; i < keys.length; i++) {
                result.put(keys[i], values[i]);
            }
            return result;
        }
    }
}
