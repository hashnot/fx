package com.hashnot.xchange.event;

import com.google.common.collect.Sets;
import com.hashnot.xchange.async.RunnableScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractParameterlessMonitor<L, R> extends AbstractPollingMonitor {
    protected Logger log = LoggerFactory.getLogger(getClass());

    protected final Set<L> listeners = Sets.newConcurrentHashSet();

    public AbstractParameterlessMonitor(RunnableScheduler scheduler) {
        super(scheduler);
    }

    @Override
    public void run() {
        try {
            R result = getData();
            callListeners(result);
        } catch (IOException e) {
            log.warn("Error from {}", this, e);
        }

    }

    protected void callListeners(R data) {
        multiplex(listeners, data, this::callListener);
    }

    protected void addListener(L listener) {
        synchronized (listeners) {
            if (listeners.add(listener)) {
                log.info("{} + {}", this, listener);
                enable();
            }
        }
    }

    protected void removeListener(L listener) {
        synchronized (listeners) {
            if (listeners.remove(listener)) {
                log.info("{} - {}", this, listener);
                if (listeners.isEmpty())
                    disable();
            }
        }
    }

    abstract protected void callListener(L listener, R data);

    abstract protected R getData() throws IOException;
}
