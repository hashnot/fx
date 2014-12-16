package com.hashnot.xchange.event;

import com.hashnot.xchange.async.impl.RunnableScheduler;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractPollingMonitor implements Runnable {
    protected final RunnableScheduler scheduler;
    private volatile boolean running = false;

    public AbstractPollingMonitor(RunnableScheduler scheduler) {
        this.scheduler = scheduler;
    }

    synchronized protected void enable() {
        if (!running) {
            scheduler.addTask(this);
            running = true;
        }
    }

    protected void disable() {
        if (running) {
            scheduler.removeTask(this);
            running = false;
        }
    }
}
