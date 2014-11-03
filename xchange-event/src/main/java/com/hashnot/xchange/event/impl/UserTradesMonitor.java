package com.hashnot.xchange.event.impl;

import com.hashnot.xchange.event.IUserTradesListener;
import com.hashnot.xchange.event.ITradesMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.UserTrades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Rafał Krupiński
 */
public class UserTradesMonitor extends AbstractPollingMonitor implements ITradesMonitor {
    final private Logger log = LoggerFactory.getLogger(UserTradesMonitor.class);

    private final Exchange parent;

    private final Set<IUserTradesListener> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void run() {
        try {
            UserTrades trades = parent.getPollingTradeService().getTradeHistory();

            for (IUserTradesListener listener : this.listeners)
                try {
                    listener.trades(trades);
                } catch (RuntimeException e) {
                    log.warn("Error", e);
                }
        } catch (IOException e) {
            log.error("Error", e);
        }
    }

    public UserTradesMonitor(Exchange parent, RunnableScheduler scheduler) {
        super(scheduler);
        this.parent = parent;
    }

    @Override
    public void addTradesListener(IUserTradesListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
            enable();
        }
    }

    @Override
    public void removeTradesListener(IUserTradesListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
            if (listeners.isEmpty())
                disable();
        }
    }
}
