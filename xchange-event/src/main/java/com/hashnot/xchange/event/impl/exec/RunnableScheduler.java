package com.hashnot.xchange.event.impl.exec;

/**
 * @author Rafał Krupiński
 */
public interface RunnableScheduler {
    void enable(Runnable r);

    void disable(Runnable r);
}
