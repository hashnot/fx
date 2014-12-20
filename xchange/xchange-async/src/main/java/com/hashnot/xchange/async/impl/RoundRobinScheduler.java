package com.hashnot.xchange.async.impl;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.hashnot.xchange.ext.util.MDCRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Intended to run as timer task
 *
 * @author Rafał Krupiński
 */
final public class RoundRobinScheduler implements Runnable, RunnableScheduler, RoundRobinSchedulerMBean {
    final private static Logger log = LoggerFactory.getLogger(RoundRobinScheduler.class);

    final private Executor executor;

    private final BlockingQueue<Runnable> priorityQueue = new LinkedBlockingQueue<>();
    final private Set<Runnable> runnables = Sets.newConcurrentHashSet();
    private final Iterator<Runnable> iterator = Iterators.cycle(runnables);
    private final AtomicBoolean skipPriorityQueue = new AtomicBoolean(false);

    public RoundRobinScheduler(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void run() {
        // unless skipPriorityQueue was set (when we run a task from it in the previous run)
        if (skipPriorityQueue.compareAndSet(true, false)) {
            if (!runTask())
                runPriority();
        } else {
            // try running a priority task
            if (!runPriority())
                runTask();
        }
    }

    protected boolean runTask() {
        if (!iterator.hasNext()) {
            return false;
        }
        tryRun(iterator.next());
        return true;
    }

    protected boolean runPriority() {
        Runnable priority = priorityQueue.poll();
        if (priority == null) {
            return false;
        }
        skipPriorityQueue.set(true);
        tryRun(priority);
        return true;
    }

    private void tryRun(Runnable runnable) {
        try {
            executor.execute(runnable);
        } catch (Exception e) {
            log.error("Error queuing {}", runnable, e);
        } catch (Error e) {
            log.error("Error", e);
            throw e;
        }
    }

    public void addTask(Runnable r) {
        assert r != null;
        assert r instanceof MDCRunnable;
        runnables.add(r);
    }

    public void removeTask(Runnable r) {
        runnables.remove(r);
    }

    public void execute(Runnable r) {
        assert r != null;
        assert !(r instanceof MDCRunnable);
        priorityQueue.add(new MDCRunnable(r));
    }

    @Override
    public Collection<String> getPriority() {
        return priorityQueue.stream().map(Object::toString).collect(Collectors.toList());
    }

    @Override
    public Collection<String> getTasks() {
        return runnables.stream().map(Object::toString).collect(Collectors.toList());
    }
}
