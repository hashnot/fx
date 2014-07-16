package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ExchangeUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;

/**
 * @author Rafał Krupiński
 */
public class StatusMonitor implements Runnable {
    final private static Logger log = LoggerFactory.getLogger(StatusMonitor.class);
    private final Queue<ExchangeUpdateEvent> exchangeUpdates;
    private final Queue<CacheUpdateEvent> cacheUpdates;
    private final Queue<OrderUpdateEvent> orderUpdates;


    public StatusMonitor(Queue<ExchangeUpdateEvent> exchangeUpdates, Queue<CacheUpdateEvent> cacheUpdates, Queue<OrderUpdateEvent> orderUpdates) {
        this.exchangeUpdates = exchangeUpdates;
        this.cacheUpdates = cacheUpdates;
        this.orderUpdates = orderUpdates;
    }

    @Override
    public void run() {
        log.info("exchange updates: {}\tcache updates: {}\torder updates: {}", exchangeUpdates.size(), cacheUpdates.size(), orderUpdates.size());
    }
}
