package com.hashnot.xchange.async.impl;

import com.hashnot.xchange.ext.util.MDCRunnable;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class RoundRobinSchedulerTest {

    @Test
    public void testRun() throws Exception {
        RoundRobinScheduler sched = new RoundRobinScheduler(Runnable::run);

        Runnable runnable = mock(MDCRunnable.class);
        sched.addTask(runnable);

        sched.run();
        verify(runnable).run();

        reset(runnable);
        sched.run();
        verify(runnable).run();
    }

    @Test
    public void testMultiTask() throws Exception {
        RoundRobinScheduler sched = new RoundRobinScheduler(Runnable::run);

        Runnable task0 = mock(MDCRunnable.class, "task0");
        Runnable task1 = mock(MDCRunnable.class, "task1");
        sched.addTask(task0);
        sched.addTask(task1);

        sched.run();
        sched.run();

        verify(task0).run();
        verify(task1).run();
    }

    @Test
    public void testRunPriority() throws Exception {
        RoundRobinScheduler sched = new RoundRobinScheduler(Runnable::run);

        Runnable task = mock(MDCRunnable.class);
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