package com.hashnot.fx.framework;

import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Rafał Krupiński
 */
public class ConfigurableThreadFactory implements ThreadFactory {
    ThreadFactory backend = Executors.defaultThreadFactory();

    private static String FORMAT = "pool-%d-thread-%d";
    private String format = FORMAT;

    private static AtomicInteger POOL_COUNT = new AtomicInteger();
    private int poolCount = POOL_COUNT.incrementAndGet();
    private AtomicInteger threadCount = new AtomicInteger();

    private boolean daemon;

    @Override
    @Nonnull
    public Thread newThread(@Nonnull Runnable r) {
        Thread result = backend.newThread(r);
        result.setName(String.format(format, poolCount, threadCount.incrementAndGet()));
        result.setUncaughtExceptionHandler((t, e) -> LoggerFactory.getLogger(r.getClass()).warn("Uncaught throwable", e));
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
