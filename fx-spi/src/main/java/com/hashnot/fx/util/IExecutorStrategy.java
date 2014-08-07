package com.hashnot.fx.util;

import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Rafał Krupiński
 */
public interface IExecutorStrategy {
    public void start();

    public void stop();

    boolean isStarted();
}
