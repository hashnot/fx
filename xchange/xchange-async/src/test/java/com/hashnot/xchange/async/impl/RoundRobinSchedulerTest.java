package com.hashnot.xchange.async.impl;

import org.junit.Test;

import static org.mockito.Mockito.*;

public class RoundRobinSchedulerTest {

    @Test
    public void testRun() throws Exception {
        RoundRobinScheduler sched = new RoundRobinScheduler(Runnable::run, "test");

        Runnable runnable = mock(Runnable.class);
        sched.addTask(runnable);

        sched.run();
        verify(runnable).run();

        reset(runnable);
        sched.run();
        verify(runnable).run();
    }
    @Test
    public void testMultiTask() throws Exception {
        RoundRobinScheduler sched = new RoundRobinScheduler(Runnable::run, "test");

        Runnable runnable = mock(Runnable.class);
        Runnable run1 = mock(Runnable.class);
        sched.addTask(runnable);
        sched.addTask(run1);

        sched.run();
        verify(run1, never()).run();
        verify(runnable).run();

        reset(runnable);
        sched.run();
        verify(run1).run();

        reset(run1);
        sched.run();
        verify(runnable).run();
    }

    @Test
    public void testRunPriority() throws Exception {
        RoundRobinScheduler sched = new RoundRobinScheduler(Runnable::run, "test");

        Runnable task = mock(Runnable.class);
        sched.addTask(task);

        Runnable priority = mock(Runnable.class);
        sched.execute(priority);

        sched.run();
        verify(priority).run();

        sched.run();
        verify(task).run();

        reset(task);
        sched.run();
        verify(task).run();
    }
}