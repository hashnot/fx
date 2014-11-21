package com.hashnot.xchange.async;

/**
 * @author Rafał Krupiński
 */
public interface IExecutorStrategyFactory {
    IExecutorStrategy create(Runnable runnable);
}
