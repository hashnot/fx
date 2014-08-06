package com.hashnot.fx.util;

/**
 * @author Rafał Krupiński
 */
public class BusyLoopExecutorFactory implements IExecutorStrategyFactory {
    @Override
    public IExecutorStrategy create(Runnable runnable) {
        return new BusyLoopExecutor(runnable);
    }
}
