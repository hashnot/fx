package com.hashnot.fx.spi.ext;

/**
 * @author Rafał Krupiński
 */
public interface RunnableScheduler {
    void enable(Runnable r);

    void disable(Runnable r);
}
