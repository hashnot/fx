package com.hashnot.xchange.async;

import com.google.common.collect.Iterators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Intended to run as timer task
 *
 * @author Rafał Krupiński
 */
final public class RoundRobinScheduler implements Runnable, RunnableScheduler {
    final private static Logger log = LoggerFactory.getLogger(RoundRobinScheduler.class);

    final private Executor executor;
    final private String name;

    private final BlockingQueue<Runnable> priorityQueue = new LinkedBlockingQueue<>();
    private final Set<Runnable> runnables = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Iterator<Runnable> iterator = Iterators.cycle(runnables);
    private final AtomicBoolean skipPriorityQueue = new AtomicBoolean(false);

    public RoundRobinScheduler(Executor executor, String name) {
        this.executor = executor;
        this.name = name;
    }

    @Override
    public void run() {
        MDC.put("name", name);
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
        } finally {
            MDC.clear();
        }
    }

    protected boolean runTask() {
        if (!iterator.hasNext()) {
            return false;
        }
        executor.execute(iterator.next()::run);
        return true;
    }

    protected boolean runPriority() {
        Runnable priority = priorityQueue.poll();
        if (priority == null) {
            return false;
        }
        skipPriorityQueue.set(true);
        executor.execute(priority::run);
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
