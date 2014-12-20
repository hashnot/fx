package com.hashnot.xchange.event;

import com.google.common.util.concurrent.SettableFuture;
import com.hashnot.xchange.async.impl.AsyncSupport;
import com.hashnot.xchange.async.impl.ListenerMap;
import com.hashnot.xchange.async.impl.RunnableScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

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
        for (Map.Entry<P, Collection<L>> e : listeners.map()) {
            P key = e.getKey();
            getData(key, result -> handleResult(key, e.getValue(), result));
        }
    }

    protected void handleResult(P param, Collection<L> listeners, Future<I> resultFuture) {
        for (L listener : listeners) {
            try {
                I intermediate = AsyncSupport.get(resultFuture);
                if (intermediate != null)
                    listenerFunction.accept(listener, wrap(param, intermediate));
            } catch (IOException e) {
                log.warn("Error from {} @{}", param, this, e);
            }
        }
    }

    abstract protected R wrap(P param, I intermediate);

    abstract protected void getData(P param, Consumer<Future<I>> consumer);

    protected void get(Future<I> future, Consumer<Future<R>> consumer, Function<I, R> wrapFunction) {
        SettableFuture<R> futureEvent = SettableFuture.create();
        try {
            I intermediate = AsyncSupport.get(future);
            R result = wrapFunction.apply(intermediate);
            futureEvent.set(result);
        } catch (IOException e) {
            futureEvent.setException(e);
        }
        consumer.accept(futureEvent);
    }

}
