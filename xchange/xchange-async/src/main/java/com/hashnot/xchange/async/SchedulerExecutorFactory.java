package com.hashnot.xchange.async;

import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Rafał Krupiński
 */
public class SchedulerExecutorFactory implements IExecutorStrategyFactory {
    final private ScheduledExecutorService executor;
    final private int rate;

    public SchedulerExecutorFactory(ScheduledExecutorService executor, int rate) {
        this.executor = executor;
        this.rate = rate;
    }

    @Override
    public IExecutorStrategy create(Runnable runnable) {
        return new SchedulerExecutor(runnable, executor, rate);
    }
}
