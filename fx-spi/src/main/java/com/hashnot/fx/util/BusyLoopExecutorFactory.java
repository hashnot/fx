package com.hashnot.fx.util;

import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Rafał Krupiński
 */
public class BusyLoopExecutorFactory implements IExecutorStrategyFactory {
    final private ScheduledExecutorService executor;

    public BusyLoopExecutorFactory(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public IExecutorStrategy create(Runnable runnable) {
        return new BusyLoopExecutor(runnable, executor);
    }
}
