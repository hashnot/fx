package com.hashnot.xchange.event;

import com.hashnot.xchange.async.impl.ListenerCollection;
import com.hashnot.xchange.async.impl.RunnableScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @param <L> listener type
 * @param <R> result type (wrapped)
 * @param <I> intermediate (raw) result type
 * @author Rafał Krupiński
 */
public abstract class AbstractParameterLessMonitor<L, R, I> extends AbstractPollingMonitor {
    protected Logger log = LoggerFactory.getLogger(getClass());

    final private BiConsumer<L, R> listenerFunction;
    final protected ListenerCollection<L> listeners = new ListenerCollection<>(this, this::enable, this::disable);

    protected AbstractParameterLessMonitor(RunnableScheduler scheduler, BiConsumer<L, R> listenerFunction, String name) {
        super(scheduler, name);
        this.listenerFunction = listenerFunction;
    }

    @Override
    public void run() {
        getData(this::handleResult);
    }

    protected final void handleResult(Future<I> future) {
        try {
            I intermediate = future.get();
            R result = wrap(intermediate);
            listeners.fire(result, listenerFunction);
        } catch (InterruptedException e) {
            log.warn("Interrupted!", e);
        } catch (ExecutionException e) {
            log.warn("Error from {}", this, e.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    protected R wrap(I intermediate) {
        return (R) intermediate;
    }

    abstract protected void getData(Consumer<Future<I>> consumer);
}
