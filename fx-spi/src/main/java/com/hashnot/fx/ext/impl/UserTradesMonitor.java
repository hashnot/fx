package com.hashnot.fx.ext.impl;

import com.hashnot.fx.ext.ITradesListener;
import com.hashnot.fx.ext.ITradesMonitor;
import com.hashnot.fx.spi.ext.RunnableScheduler;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Rafał Krupiński
 */
public class UserTradesMonitor extends AbstractPollingMonitor implements ITradesMonitor, Runnable {
    final private Logger log = LoggerFactory.getLogger(UserTradesMonitor.class);

    private final PollingTradeService service;

    private final Set<ITradesListener> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void run() {
        try {
            Trades trades = service.getTradeHistory();

            for (ITradesListener listener : this.listeners)
                listener.trades(trades);
        } catch (IOException e) {
            log.error("Error", e);
        }
    }

    public UserTradesMonitor(PollingTradeService service, RunnableScheduler scheduler) {
        super(scheduler);
        this.service = service;
    }

    @Override
    public void addTradesListener(ITradesListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
            enable();
        }
    }

    @Override
    public void removeTradesListener(ITradesListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
            if (listeners.isEmpty())
                disable();
        }
    }
}
