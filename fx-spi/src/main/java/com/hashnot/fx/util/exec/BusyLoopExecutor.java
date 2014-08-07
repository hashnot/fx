package com.hashnot.fx.util.exec;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Rafał Krupiński
 */
public class BusyLoopExecutor implements IExecutorStrategy {
    private AtomicBoolean run = new AtomicBoolean(false);
    private final Runnable runnable;
    private ScheduledExecutorService executor;

    public BusyLoopExecutor(Runnable runnable, ScheduledExecutorService executor) {
        this.runnable = runnable;
        this.executor = executor;
    }

    @Override
    public void start() {
        if (!run.compareAndSet(false, true)) return;
        executor.execute(() -> {
            while (run.get())
                runnable.run();
        });
    }

    @Override
    public void stop() {
        run.set(false);
    }

    @Override
    public boolean isStarted() {
        return run.get();
    }
}
