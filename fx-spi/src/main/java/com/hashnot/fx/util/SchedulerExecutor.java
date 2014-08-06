package com.hashnot.fx.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Rafał Krupiński
 */
public class SchedulerExecutor implements IExecutorStrategy {
    final private static Logger log = LoggerFactory.getLogger(SchedulerExecutor.class);
    private final long rate;
    private ScheduledFuture<?> future;
    private final Runnable runnable;

    public SchedulerExecutor(Runnable runnable, long rate) {
        this.rate = rate;
        this.runnable = runnable;
    }

    @Override
    public void start(ScheduledExecutorService scheduler) {
        synchronized (this) {
            if (future != null) return;
        }
        future = scheduler.scheduleAtFixedRate(this::run, 0, rate, TimeUnit.MILLISECONDS);
    }

    public void run() {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            log.warn("Error", e);
        }
    }

    @Override
    public synchronized void stop() {
        future.cancel(false);
        future = null;
    }

    @Override
    public synchronized boolean isStarted() {
        return future != null;
    }
}
