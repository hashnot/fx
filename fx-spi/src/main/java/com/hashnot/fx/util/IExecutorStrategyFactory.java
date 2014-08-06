package com.hashnot.fx.util;

/**
 * @author Rafał Krupiński
 */
public interface IExecutorStrategyFactory {
    IExecutorStrategy create(Runnable runnable);
}
