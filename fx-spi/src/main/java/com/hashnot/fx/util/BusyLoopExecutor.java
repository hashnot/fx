package com.hashnot.fx.util;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Rafał Krupiński
 */
public class BusyLoopExecutor implements IExecutorStrategy {
    private AtomicBoolean run = new AtomicBoolean(false);
    private final Runnable runnable;

    public BusyLoopExecutor(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void start(ScheduledExecutorService scheduler) {
        if (!run.compareAndSet(false, true)) return;
        scheduler.execute(() -> {
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
