package com.hashnot.xchange.async;

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