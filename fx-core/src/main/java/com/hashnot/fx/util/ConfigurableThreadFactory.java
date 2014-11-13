package com.hashnot.fx.util;

import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Rafał Krupiński
 */
public class ConfigurableThreadFactory implements ThreadFactory {
    private final static String FORMAT = "%d/%d";
    private String format = FORMAT;

    private final static AtomicInteger POOL_COUNT = new AtomicInteger();
    private final int poolCount = POOL_COUNT.getAndIncrement();
    private AtomicInteger threadCount = new AtomicInteger();

    public static Thread.UncaughtExceptionHandler exceptionHandler = (t, e) -> LoggerFactory.getLogger("ERROR").warn("Uncaught throwable", e);

    private ThreadGroup threadGroup = new ThreadGroup("Group-" + poolCount);

    private boolean daemon;

    @Override
    @Nonnull
    public Thread newThread(@Nonnull Runnable r) {
        Thread result = new Thread(threadGroup, r, String.format(format, poolCount, threadCount.getAndIncrement()));

        result.setUncaughtExceptionHandler(exceptionHandler);
        result.setDaemon(daemon);
        return result;
    }

    public ConfigurableThreadFactory() {
    }

    public ConfigurableThreadFactory(String format) {
        this.format = format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }
}
