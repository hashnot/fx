package com.hashnot.xchange.async;

import java.util.concurrent.Executor;

/**
 * @author Rafał Krupiński
 */
public interface IExecutorStrategy {
    public void start();

    public void stop();

    boolean isStarted();

    Executor getExecutor();
}
