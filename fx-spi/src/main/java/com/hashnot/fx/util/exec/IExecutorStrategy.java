package com.hashnot.fx.util.exec;

import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Rafał Krupiński
 */
public interface IExecutorStrategy {
    public void start();

    public void stop();

    boolean isStarted();
}
