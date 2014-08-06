package com.hashnot.fx.util;

import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Rafał Krupiński
 */
public interface IExecutorStrategy {
    public void start(ScheduledExecutorService scheduler);

    public void stop();

    boolean isStarted();
}
