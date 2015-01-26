package com.hashnot.xchange.async.impl;

import com.google.common.util.concurrent.SettableFuture;
import com.xeiam.xchange.FrequencyLimitExceededException;
import com.xeiam.xchange.NonceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
final public class AsyncSupport {
    final protected static Logger log = LoggerFactory.getLogger(AsyncSupport.class);

    public static <T> Future<T> call(Callable<T, IOException> call, Consumer<Future<T>> consumer, Executor executor) {
        SettableFuture<T> future = SettableFuture.create();
        executor.execute(new CallableWrapper<>(executor, call, consumer, future));
        return future;
    }

    static class CallableWrapper<T, E extends IOException> implements Runnable {
        final Executor executor;
        final Callable<T, E> call;
        final Consumer<Future<T>> consumer;
        final SettableFuture<T> future;

        CallableWrapper(Executor executor, Callable<T, E> call, Consumer<Future<T>> consumer, SettableFuture<T> future) {
            this.executor = executor;
            this.call = call;
            this.consumer = consumer;
            this.future = future;
        }

        @Override
        public void run() {
            try {
                T result = call.call();
                future.set(result);
            } catch (NonceException | FrequencyLimitExceededException e) {
                log.warn("Error from {}", call, e);
                executor.execute(this);
                return;
            } catch (Exception e) {
                future.setException(e);
            } catch (Error e) {
                log.warn("Error from {}", call, e);
                throw e;
            }
            if (consumer != null)
                consumer.accept(future);

        }
    }

    public static <T> T get(Future<T> future) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted!", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            if (cause instanceof Error)
                throw (Error) cause;
            else if (cause instanceof IOException)
                throw (IOException) cause;
            else
                throw new RuntimeException("Unexpected exception", cause);
        }
    }
}
