package com.hashnot.xchange.async;

import com.google.common.collect.Iterators;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Intended to run as timer task
 *
 * @author Rafał Krupiński
 */
public class RoundRobinScheduler implements Runnable, RunnableScheduler {
    private final BlockingQueue<Runnable> priorityQueue = new LinkedBlockingQueue<>();

    private Set<Runnable> runnables = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Iterator<Runnable> iterator = Iterators.cycle(runnables);

    private final AtomicBoolean skipPriorityQueue = new AtomicBoolean(false);

    @Override
    public void run() {
        if (skipPriorityQueue.compareAndSet(false, false)) {
            Runnable priority = priorityQueue.poll();
            if (priority != null) {
                priority.run();
                skipPriorityQueue.set(true);
                return;
            }
        }
        Runnable task;
        synchronized (this) {
            if (iterator.hasNext())
                task = iterator.next();
            else
                return;
        }
        task.run();
    }

    public synchronized void addTask(Runnable r) {
        runnables.add(r);
    }

    public synchronized void removeTask(Runnable r) {
        runnables.remove(r);
    }

    public void priority(Runnable r) {
        priorityQueue.add(r);
    }
}
