package com.hashnot.xchange.event.impl.exec;

/**
 * @author Rafał Krupiński
 */
public interface IExecutorStrategy {
    public void start();

    public void stop();

    boolean isStarted();
}
