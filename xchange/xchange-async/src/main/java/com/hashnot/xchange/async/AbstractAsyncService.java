package com.hashnot.xchange.async;

import com.google.common.util.concurrent.SettableFuture;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class AbstractAsyncService {
    final private Executor executor;

    public AbstractAsyncService(Executor executor) {
        this.executor = executor;
    }

    protected <T> Future<T> call(RemoteCall<T> call, Consumer<Future<T>> consumer) {
        SettableFuture<T> future = SettableFuture.create();
        executor.execute(() -> {
            try {
                future.set(call.call());
            } catch (RuntimeException | IOException e) {
                future.setException(e);
            }
            if (consumer != null)
                consumer.accept(future);
        });
        return future;
    }

    protected static interface RemoteCall<T> {
        T call() throws IOException;
    }
}
