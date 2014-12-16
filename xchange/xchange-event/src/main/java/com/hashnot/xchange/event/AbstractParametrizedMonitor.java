package com.hashnot.xchange.event;

import com.google.common.collect.Sets;
import com.hashnot.xchange.async.RunnableScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractParametrizedMonitor<P, L, R> extends AbstractPollingMonitor {
    final protected Logger log = LoggerFactory.getLogger(getClass());

    final protected Map<P, Collection<L>> listeners = new ConcurrentHashMap<>();

    public AbstractParametrizedMonitor(RunnableScheduler scheduler) {
        super(scheduler);
    }

    @Override
    public void run() {
        for (Map.Entry<P, Collection<L>> e : listeners.entrySet()) {
            getData(e.getKey(), (data) -> callListeners(data, e.getValue()));
        }
    }

    protected void callListeners(R data, Iterable<L> listeners) {
        multiplex(listeners, data, this::callListener);
    }

    protected void addListener(L listener, P param) {
        listeners.compute(param, (k, v) -> {
            if (v == null)
                v = Sets.newConcurrentHashSet();
            if (v.add(listener)) {
                log.info("{}@{} + {}", param, this, listener);
                enable();
            }
            return v;
        });
    }

    protected void removeListener(L listener, P param) {
        listeners.computeIfPresent(param, (k, v) -> {
            if (v.remove(listener)) {
                log.info("{}@{} - {}", param, this, listener);
                if (v.isEmpty()) {
                    disable();
                    return null;
                }
            }
            return v;
        });
    }

    abstract protected void callListener(L listener, R data);

    abstract protected void getData(P param, Consumer<R> consumer);

    public Map<String, String> getListeners() {
        Map<String, String> result = new HashMap<>();
        listeners.forEach((k, v) -> result.put(k.toString(), String.join(" ", v.toString())));
        return result;
    }

}
