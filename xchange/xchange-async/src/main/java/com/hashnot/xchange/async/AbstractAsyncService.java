package com.hashnot.xchange.async;

import com.google.common.util.concurrent.SettableFuture;
import com.hashnot.xchange.ext.util.MDCRunnable;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class AbstractAsyncService {
    final private Executor remoteExecutor;
    final private Executor consumerExecutor;

    public AbstractAsyncService(Executor remoteExecutor, Executor consumerExecutor) {
        this.remoteExecutor = remoteExecutor;
        this.consumerExecutor = consumerExecutor;
    }

    protected <T> Future<T> call(RemoteCall<T> call, Consumer<Future<T>> consumer) {
        SettableFuture<T> future = SettableFuture.create();
        remoteExecutor.execute(() -> {
            try {
                T result = call.call();
                future.set(result);
            } catch (RuntimeException | IOException e) {
                future.setException(e);
            }
            if (consumer != null)
                consumerExecutor.execute(new MDCRunnable(() -> consumer.accept(future)));
        });
        return future;
    }

    protected static interface RemoteCall<T> {
        T call() throws IOException;
    }
}
