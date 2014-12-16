package com.hashnot.xchange.event;

import com.hashnot.xchange.async.impl.ListenerCollection;
import com.hashnot.xchange.async.impl.RunnableScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.BiConsumer;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractParameterLessMonitor<L, R> extends AbstractPollingMonitor {
    protected Logger log = LoggerFactory.getLogger(getClass());

    final private BiConsumer<L, R> listenerFunction;
    final protected ListenerCollection<L> listeners = new ListenerCollection<>(this, this::enable, this::disable);

    public AbstractParameterLessMonitor(RunnableScheduler scheduler, BiConsumer<L, R> listenerFunction) {
        super(scheduler);
        this.listenerFunction = listenerFunction;
    }

    @Override
    public void run() {
        try {
            R result = getData();
            listeners.fire(result, listenerFunction);
        } catch (IOException e) {
            log.warn("Error from {}", this, e);
        }
    }

    abstract protected R getData() throws IOException;
}
