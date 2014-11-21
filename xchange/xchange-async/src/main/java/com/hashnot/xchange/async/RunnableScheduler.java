package com.hashnot.xchange.async;

/**
 * Scheduler for {@link Runnable}s. Supports cyclic and priority tasks. Cyclic tasks are called round-robin.
 * Before calling a cyclic task, a single task is taken from the priority queue and called.
 * If there is more than one task in the priority queue, one task from the cyclic and one priority task is called, alternately
 *
 * @author Rafał Krupiński
 */
public interface RunnableScheduler {
    /**
     * Add cyclic task
     */
    void addTask(Runnable r);

    /**
     * remove cyclic task
     */
    void removeTask(Runnable r);

    /**
     * Add single-call priority task
     */
    void priority(Runnable r);
}
