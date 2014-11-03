package com.hashnot.xchange.event.impl;

/**
 * @author Rafał Krupiński
 */
public interface RunnableScheduler {
    void enable(Runnable r);

    void disable(Runnable r);
}
