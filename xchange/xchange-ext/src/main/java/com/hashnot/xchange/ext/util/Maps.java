package com.hashnot.xchange.ext.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
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

    public static <K, V> Map<K, V> map(Collection<K> keys, Collection<V> values) {
        assert keys.size() == values.size();
        Map<K, V> result = new HashMap<>(keys.size());
        Iterator<V> vi = values.iterator();
        for (K key : keys) {
            V value = vi.next();
            result.put(key, value);
        }
        return result;
    }

    @SafeVarargs
    public static <K> MapMaker<K> keys(K... keys) {
        return new MapMaker<>(keys);
    }

    public static class MapMaker<K> {
        private K[] keys;

        public MapMaker(K[] keys) {
            this.keys = keys;
        }

        public <V> Map<K, V> map(V... values) {
            assert keys.length == values.length;
            Map<K, V> result = new HashMap<>(keys.length);
            for (int i = 0; i < keys.length; i++) {
                result.put(keys[i], values[i]);
            }
            return result;
        }
    }
}
