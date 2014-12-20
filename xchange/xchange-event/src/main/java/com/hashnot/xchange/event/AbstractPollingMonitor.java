package com.hashnot.xchange.event;

import com.hashnot.xchange.async.impl.RunnableScheduler;
import com.hashnot.xchange.ext.util.MDCRunnable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractPollingMonitor implements Runnable {
    protected final RunnableScheduler scheduler;

    final private Runnable runnable;

    private volatile boolean running = false;

    public AbstractPollingMonitor(RunnableScheduler scheduler, String name) {
        this.scheduler = scheduler;
        Map<String, String> mdc = new HashMap<>(1);
        mdc.put("name", name);
        runnable = new MDCRunnable(this, mdc);
    }

    synchronized protected void enable() {
        if (!running) {
            scheduler.addTask(runnable);
            running = true;
        }
    }

    protected void disable() {
        if (running) {
            scheduler.removeTask(runnable);
            running = false;
        }
    }
}
