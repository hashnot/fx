package com.hashnot.xchange.event.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.xchange.event.impl.exec.RunnableScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractParametrizedMonitor<P, L, R> extends AbstractPollingMonitor {
    public AbstractParametrizedMonitor(RunnableScheduler scheduler, Executor executor) {
        super(scheduler);
        this.executor = executor;
    }

    protected Executor executor;

    final protected Multimap<P, L> listeners = Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);

    final protected Logger log = LoggerFactory.getLogger(getClass());


    @Override
    public void run() {
        for (Map.Entry<P, Collection<L>> e : listeners.asMap().entrySet()) {

            try {
                R data = getData(e.getKey());
                if (data == null)
                    return;

                executor.execute(new ListenerNotifier(data, e.getValue()));
            } catch (IOException x) {
                log.warn("Error from {}", this, x);
                return;
            }

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
            for (L listener : listeners) {
                try {
                    notifyListener(listener, data);
                } catch (RuntimeException x) {
                    log.warn("Error from {}", listener, x);
                }
            }

        }

    }

    protected abstract void notifyListener(L listener, R data);

    abstract protected R getData(P param) throws IOException;
}
