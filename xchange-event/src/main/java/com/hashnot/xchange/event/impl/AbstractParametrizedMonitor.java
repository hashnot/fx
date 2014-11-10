package com.hashnot.xchange.event.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.xchange.event.impl.exec.RunnableScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractParametrizedMonitor<P, L, R> extends AbstractPollingMonitor {
    public AbstractParametrizedMonitor(RunnableScheduler scheduler, Executor executor) {
        super(scheduler);
        this.executor = executor;
    }

    protected Executor executor;

    final protected Multimap<P, L> listeners = Multimaps.newSetMultimap(new HashMap<>(), () -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

    final protected Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void run() {
        for (Map.Entry<P, Collection<L>> e : listeners.asMap().entrySet()) {

            try {
                R data = getData(e.getKey());
                if (data == null)
                    return;

                callListeners(data, e.getValue());
            } catch (IOException x) {
                log.warn("Error from {}", this, x);
                return;
            }

        }
    }

    protected void callListeners(R data, Collection<L> listeners) {
        if (!listeners.isEmpty())
            executor.execute(new ListenerNotifier(data, listeners));
    }

    protected void addListener(L listener, P param) {
        synchronized (listeners) {
            boolean put = listeners.put(param, listener);
            if (put)
                log.debug("{} listens on {} @{}", listener, param, this);

            enable();
        }
    }

    protected void removeListener(L listener, P param) {
        synchronized (listeners) {
            boolean remove = listeners.remove(param, listener);
            if (remove)
                log.debug("{} doesn't listen on {} @{}", listener, param, this);

            if (listeners.isEmpty())
                disable();
        }
    }

    private class ListenerNotifier implements Runnable {
        protected R data;
        protected Collection<L> listeners;

        private ListenerNotifier(R data, Collection<L> listeners) {
            this.data = data;
            this.listeners = listeners;
        }

        @Override
        public void run() {
            multiplex(listeners, data, (l, e) -> callListener(l, e));
        }
    }

    abstract protected void callListener(L listener, R data);

    abstract protected R getData(P param) throws IOException;
}
