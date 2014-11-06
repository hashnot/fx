package com.hashnot.xchange.event.impl.exec;

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
