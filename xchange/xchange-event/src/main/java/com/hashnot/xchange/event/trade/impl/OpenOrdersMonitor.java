package com.hashnot.xchange.event.trade.impl;

import com.hashnot.xchange.async.RunnableScheduler;
import com.hashnot.xchange.event.AbstractParameterLessMonitor;
import com.hashnot.xchange.event.trade.IOpenOrdersListener;
import com.hashnot.xchange.event.trade.IOpenOrdersMonitor;
import com.hashnot.xchange.event.trade.OpenOrdersEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.OpenOrders;

import java.io.IOException;

/**
 * @author Rafał Krupiński
 */
public class OpenOrdersMonitor extends AbstractParameterLessMonitor<IOpenOrdersListener, OpenOrdersEvent> implements IOpenOrdersMonitor {
    final private Exchange exchange;

    public OpenOrdersMonitor(Exchange exchange, RunnableScheduler scheduler) {
        super(scheduler);
        this.exchange = exchange;
    }

    @Override
    protected OpenOrdersEvent getData() throws IOException {
        OpenOrders orders = exchange.getPollingTradeService().getOpenOrders();
        return new OpenOrdersEvent(exchange, orders);
    }

    @Override
    protected void callListener(IOpenOrdersListener listener, OpenOrdersEvent evt) {
        listener.openOrders(evt);
    }

    @Override
    public void addOpenOrdersListener(IOpenOrdersListener listener) {
        addListener(listener);
    }

    @Override
    public void removeOpenOrdersListener(IOpenOrdersListener listener) {
        removeListener(listener);
    }

    @Override
    public String toString() {
        return "OpenOrdersMonitor@" + exchange;
    }
}
