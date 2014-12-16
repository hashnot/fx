package com.hashnot.xchange.async.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * @author Rafał Krupiński
 */
public class ListenerCollection<L> {
    final protected Logger log = LoggerFactory.getLogger(getClass());

    final private Object owner;
    final private Runnable enable;
    final private Runnable disable;

    protected final Map<L, Boolean> listeners = new ConcurrentHashMap<>();

    public ListenerCollection(Object owner) {
        this(owner, null, null);
    }

    public ListenerCollection(Object owner, Runnable enable, Runnable disable) {
        this.owner = owner;
        this.enable = enable;
        this.disable = disable;
    }

    public void addListener(L listener) {
        listeners.compute(listener, this::handleAddListener);
    }

    protected Boolean handleAddListener(L listener, Boolean v) {
        if (v == null) {
            v = Boolean.TRUE;
            log.info("{} + {}", owner, listener);
            if (enable != null)
                enable.run();
        }
        return v;
    }

    public void removeListener(L listener) {
        listeners.computeIfPresent(listener, this::handleRemoveListener);
    }

    protected Boolean handleRemoveListener(L listener, Boolean v) {
        if (v != null) {
            log.info("{} - {}", owner, listener);
            if (listeners.isEmpty()) {
                if (disable != null)
                    disable.run();
                return null;
            }
            return v;
        }
        return null;
    }

    public <R> void fire(R result, BiConsumer<L, R> function) {
        listeners.keySet().forEach(l -> function.accept(l, result));
    }

    public Collection<String> reportListeners() {
        return listeners.keySet().stream().map(Object::toString).collect(Collectors.toList());
    }
}
