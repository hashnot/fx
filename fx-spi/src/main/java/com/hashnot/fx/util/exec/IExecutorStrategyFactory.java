package com.hashnot.fx.util.exec;

/**
 * @author Rafał Krupiński
 */
public interface IExecutorStrategyFactory {
    IExecutorStrategy create(Runnable runnable);
}
