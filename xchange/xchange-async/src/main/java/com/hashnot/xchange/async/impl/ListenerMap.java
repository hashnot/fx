package com.hashnot.xchange.async.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * @author Rafał Krupiński
 */
public class ListenerMap<K, L> {
    final protected Logger log = LoggerFactory.getLogger(getClass());
    final private Object owner;
    final private Runnable enable;
    final private Runnable disable;

    protected final Map<K, Collection<L>> listeners = new ConcurrentHashMap<>();

    public ListenerMap(Object owner) {
        this(owner, null, null);
    }

    public ListenerMap(Object owner, Runnable enable, Runnable disable) {
        this.owner = owner;
        this.enable = enable;
        this.disable = disable;
    }

    public void addListener(K key, L listener) {
        listeners.compute(key, (k, listeners) -> {
            if (listeners == null) {
                listeners = new LinkedHashSet<>();
                listeners.add(listener);
                log.info("{} + {}", owner, listener);
                if (enable != null)
                    enable.run();
            }
            return listeners;
        });
    }

    public void removeListeners(K key) {
        listeners.remove(key);
    }

    public void removeListener(K key, L listener) {
        listeners.computeIfPresent(key, (k, listeners) -> {
            if (listeners != null) {
                log.info("{} - {}", owner, listener);
                listeners.remove(listener);
                if (listeners.isEmpty()) {
                    if (disable != null)
                        disable.run();
                    return null;
                }
                return listeners;
            }
            return null;

        });
    }

    public Iterable<K> params() {
        return listeners.keySet();
    }

    public Iterable<Map.Entry<K, Collection<L>>> map() {
        return listeners.entrySet();
    }

    public <R> void fire(K key, R result, BiConsumer<L, R> function) {
        listeners.computeIfPresent(key, (k, v) -> {
            for (L listener : v)
                function.accept(listener, result);
            return v;
        });
    }


    public Map<String, String> reportListeners() {
        Map<String, String> result = new HashMap<>();
        listeners.forEach((k, v) -> result.put(k.toString(), String.join(" ", v.stream().map(Object::toString).collect(Collectors.toList()))));
        return result;
    }
}
