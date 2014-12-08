package com.hashnot.xchange.event;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.xchange.async.RunnableScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractParametrizedMonitor<P, L, R> extends AbstractPollingMonitor {
    final protected Logger log = LoggerFactory.getLogger(getClass());

    final protected Executor executor;

    final protected Multimap<P, L> listeners = Multimaps.newSetMultimap(new HashMap<>(), LinkedHashSet::new);

    public AbstractParametrizedMonitor(RunnableScheduler scheduler, Executor executor) {
        super(scheduler);
        this.executor = executor;
    }

    @Override
    public void run() {
        for (Map.Entry<P, Collection<L>> e : listeners.asMap().entrySet()) {
            getData(e.getKey(), (data) -> {
                if (data == null)
                    log.warn("Null result from {}", this);

                callListeners(data, e.getValue());
            });
        }
    }

    protected void callListeners(R data, Iterable<L> listeners) {
        // must use executor, all calls on an exchange are made in single thread
        executor.execute(() -> multiplex(listeners, data, this::callListener));
    }

    protected void addListener(L listener, P param) {
        synchronized (listeners) {
            boolean put = listeners.put(param, listener);
            if (put) {
                log.info("{}@{} + {}", param, this, listener);
                enable();
            }
        }
    }

    protected void removeListener(L listener, P param) {
        synchronized (listeners) {
            boolean remove = listeners.remove(param, listener);
            if (remove) {
                log.info("{}@{} - {}", param, this, listener);
                if (listeners.isEmpty())
                    disable();
            }
        }
    }

    abstract protected void callListener(L listener, R data);

    abstract protected void getData(P param, Consumer<R> consumer);
}
