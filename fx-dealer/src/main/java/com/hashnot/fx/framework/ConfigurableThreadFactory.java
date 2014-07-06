package com.hashnot.fx.framework;

import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author Rafał Krupiński
 */
class ConfigurableThreadFactory implements ThreadFactory {
    ThreadFactory backend = Executors.defaultThreadFactory();

    @Override
    public Thread newThread(Runnable r) {
        Thread result = backend.newThread(r);
        result.setUncaughtExceptionHandler((t, e) -> LoggerFactory.getLogger(r.getClass()).warn("Uncaught throwable", e));
        return result;
    }
}
