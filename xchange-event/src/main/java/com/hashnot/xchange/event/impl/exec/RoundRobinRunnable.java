package com.hashnot.xchange.event.impl.exec;

import com.google.common.collect.Iterators;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intended to run as timer task
 *
 * @author Rafał Krupiński
 */
public class RoundRobinRunnable implements Runnable, RunnableScheduler {
    private final Set<Runnable> runnables = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Iterator<Runnable> iterator = Iterators.cycle(runnables);

    @Override
    public void run() {
        if (iterator.hasNext())
            iterator.next().run();
    }

    public void enable(Runnable r) {
        runnables.add(r);
    }

    public void disable(Runnable r) {
        runnables.remove(r);
    }
}
