package com.hashnot.xchange.event;

import com.hashnot.xchange.async.RunnableScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractParameterLessMonitor<L, R> extends AbstractPollingMonitor {
    protected Logger log = LoggerFactory.getLogger(getClass());

    protected final Map<L, Boolean> listeners = new ConcurrentHashMap<>();

    public AbstractParameterLessMonitor(RunnableScheduler scheduler) {
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
        multiplex(listeners.keySet(), data, this::callListener);
    }

    protected void addListener(L listener) {
        listeners.compute(listener, (k, v) -> {
            if (v == null) {
                v = Boolean.TRUE;
                log.info("{} + {}", this, listener);
                enable();
            }
            return v;
        });
    }

    protected void removeListener(L listener) {
        listeners.computeIfPresent(listener, (k, v) -> {
            if (v != null) {
                log.info("{} - {}", this, listener);
                if (listeners.isEmpty()) {
                    disable();
                    return null;
                }
                return v;
            }
            return null;
        });
    }

    abstract protected void callListener(L listener, R data);

    abstract protected R getData() throws IOException;
}
