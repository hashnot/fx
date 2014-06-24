package com.hashnot.fx.cf.ui;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Rafał Krupiński
 */
public class PasswordFuture implements Future<String>, PasswordDialog.ValueListener<String> {
    private String value;
    private volatile boolean done;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public synchronized String get() throws InterruptedException, ExecutionException {
        while (!done)
            synchronized (this) {
                wait();
            }
        return value;
    }

    @Override
    public String get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return get();
    }

    @Override
    public synchronized void setValue(String value) {
        this.value = value;
        done = true;
        notify();
    }
}
