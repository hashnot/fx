package com.hashnot.xchange.event.impl.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Rafał Krupiński
 */
public class SchedulerExecutor implements IExecutorStrategy, Runnable {
    final private static Logger log = LoggerFactory.getLogger(SchedulerExecutor.class);

    private final Runnable runnable;
    private ScheduledExecutorService executor;
    private final long rate;

    private ScheduledFuture<?> future;

    public SchedulerExecutor(Runnable runnable, ScheduledExecutorService executor, long rate) {
        this.runnable = runnable;
        this.executor = executor;
        this.rate = rate;
    }

    @Override
    public void start() {
        synchronized (this) {
            if (future != null) return;
        }
        future = executor.scheduleAtFixedRate(this, 0, rate, TimeUnit.MILLISECONDS);
    }

    public void run() {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            log.warn("Error from {}", runnable, e);
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

    @Override
    public Executor getExecutor() {
        return executor;
    }
}
