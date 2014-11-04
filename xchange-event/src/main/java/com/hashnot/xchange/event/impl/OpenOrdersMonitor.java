package com.hashnot.xchange.event.impl;

import com.hashnot.xchange.event.IOpenOrdersListener;
import com.hashnot.xchange.event.IOpenOrdersMonitor;
import com.hashnot.xchange.event.OpenOrdersEvent;
import com.hashnot.xchange.event.impl.exec.RunnableScheduler;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.OpenOrders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * @author Rafał Krupiński
 */
public class OpenOrdersMonitor extends AbstractPollingMonitor implements IOpenOrdersMonitor {
    final private static Logger log = LoggerFactory.getLogger(OpenOrdersMonitor.class);

    final private Exchange parent;

    private final Set<IOpenOrdersListener> listeners = new HashSet<>();

    public OpenOrdersMonitor(Exchange exchange, RunnableScheduler scheduler) {
        super(scheduler);
        this.parent = exchange;
    }

    public void run() {
        try {
            OpenOrders orders = parent.getPollingTradeService().getOpenOrders();
            if (orders == null) {
                log.warn("Null open orders @{}", parent);
                return;
            }

            OpenOrdersEvent evt = new OpenOrdersEvent(parent, orders);
            for (IOpenOrdersListener listener : listeners) {
                try {
                    listener.openOrders(evt);
                } catch (Exception e) {
                    log.warn("Error from {}", listener, e);
                }
            }
        } catch (IOException e) {
            log.warn("Error getting open orders");
        }
    }

    @Override
    public void addOpenOrdersListener(IOpenOrdersListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
            enable();
        }
    }

    @Override
    public void removeOpenOrdersListener(IOpenOrdersListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
            if (listeners.isEmpty())
                disable();
        }
    }
}
