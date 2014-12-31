package com.hashnot.xchange.event;

import com.hashnot.xchange.async.impl.AsyncSupport;
import com.hashnot.xchange.async.impl.ListenerMap;
import com.hashnot.xchange.async.impl.RunnableScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractParametrizedMonitor<P, L, R, I> extends AbstractPollingMonitor {
    final protected Logger log = LoggerFactory.getLogger(getClass());

    final protected ListenerMap<P, L> listeners = new ListenerMap<>(this, this::enable, this::disable);
    final private BiConsumer<L, R> listenerFunction;

    public AbstractParametrizedMonitor(RunnableScheduler scheduler, BiConsumer<L, R> listenerFunction, String name) {
        super(scheduler, name);
        this.listenerFunction = listenerFunction;
    }

    @Override
    public void run() {
        for (P key : listeners.params())
            getData(key, result -> handleResult(key, result));
    }

    protected void handleResult(P param, Future<I> resultFuture) {
        try {
            I intermediate = AsyncSupport.get(resultFuture);
            R result = wrap(param, intermediate);
            this.listeners.fire(param, resultFuture, (l, i) -> listenerFunction.accept(l, result));
        } catch (IOException e) {
            log.warn("Error from {} @{}", param, this, e);
        }
    }

    abstract protected R wrap(P param, I intermediate);

    abstract protected void getData(P param, Consumer<Future<I>> consumer);

}
