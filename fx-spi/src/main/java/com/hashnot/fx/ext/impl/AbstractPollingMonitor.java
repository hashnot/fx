package com.hashnot.fx.ext.impl;

import com.hashnot.fx.spi.ext.RunnableScheduler;

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
            scheduler.enable(this);
            running = true;
        }
    }

    protected void disable() {
        if (running) {
            scheduler.disable(this);
            running = false;
        }
    }
}
