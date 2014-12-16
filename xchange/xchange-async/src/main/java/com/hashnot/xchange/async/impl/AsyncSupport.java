package com.hashnot.xchange.async.impl;

import com.google.common.util.concurrent.SettableFuture;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
final public class AsyncSupport {
    public static <T> Future<T> call(Callable<T, IOException> call, Consumer<Future<T>> consumer, Executor executor) {
        SettableFuture<T> future = SettableFuture.create();
        executor.execute(() -> {
            try {
                T result = call.call();
                future.set(result);
            } catch (RuntimeException | IOException e) {
                future.setException(e);
            }
            if (consumer != null)
                consumer.accept(future);
        });
        return future;
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
            else if (cause instanceof IOException)
                throw (IOException) cause;
            else
                throw new RuntimeException("Unexpected exception", cause);
        }
    }

}
