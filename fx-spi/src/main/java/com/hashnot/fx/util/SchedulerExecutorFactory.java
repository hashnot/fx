package com.hashnot.fx.util;

/**
 * @author Rafał Krupiński
 */
public class SchedulerExecutorFactory implements IExecutorStrategyFactory {
    final private int rate;

    public SchedulerExecutorFactory(int rate) {
        this.rate = rate;
    }

    @Override
    public IExecutorStrategy create(Runnable runnable) {
        return new SchedulerExecutor(runnable, rate);
    }
}
