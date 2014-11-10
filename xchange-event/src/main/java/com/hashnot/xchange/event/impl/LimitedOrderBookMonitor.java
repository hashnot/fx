package com.hashnot.xchange.event.impl;

import com.hashnot.xchange.event.impl.exec.RunnableScheduler;
import com.xeiam.xchange.Exchange;

import java.util.concurrent.Executor;

/**
 * @author Rafał Krupiński
 */
public class LimitedOrderBookMonitor extends OrderBookMonitor {
    public LimitedOrderBookMonitor(Exchange exchange, RunnableScheduler runnableScheduler, Executor executor) {
        super(exchange, runnableScheduler, executor);
    }
}
