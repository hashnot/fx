package com.hashnot.xchange.async;

import com.google.common.collect.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    final private static Logger log = LoggerFactory.getLogger(RoundRobinScheduler.class);
    private final BlockingQueue<Runnable> priorityQueue = new LinkedBlockingQueue<>();

    private final Set<Runnable> runnables = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Iterator<Runnable> iterator = Iterators.cycle(runnables);

    private final AtomicBoolean skipPriorityQueue = new AtomicBoolean(false);

    @Override
    public void run() {
        try {
            // unless skipPriorityQueue was set (when we run a task from it in the previous run)
            if (!skipPriorityQueue.compareAndSet(true, false)) {
                // try running a priority task
                if (runPriority()) return;
            }
            if (!runTask()) {
                runPriority();
            }
        } catch (Throwable e) {
            log.warn("Error", e);
        }
    }

    protected boolean runTask() {
        if (!iterator.hasNext()) {
            return false;
        }
        Runnable task = iterator.next();
        task.run();
        return true;
    }

    protected boolean runPriority() {
        Runnable priority = priorityQueue.poll();
        if (priority == null) {
            return false;
        }
        skipPriorityQueue.set(true);
        priority.run();
        return true;
    }

    public synchronized void addTask(Runnable r) {
        assert r != null;
        runnables.add(r);
    }

    public synchronized void removeTask(Runnable r) {
        runnables.remove(r);
    }

    public void execute(Runnable r) {
        assert r != null;
        priorityQueue.add(r);
    }
}
