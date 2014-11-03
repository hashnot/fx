package com.hashnot.xchange.event.impl.exec;

/**
 * @author Rafał Krupiński
 */
public interface IExecutorStrategyFactory {
    IExecutorStrategy create(Runnable runnable);
}
