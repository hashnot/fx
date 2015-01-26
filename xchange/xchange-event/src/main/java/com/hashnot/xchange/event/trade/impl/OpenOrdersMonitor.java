package com.hashnot.xchange.event.trade.impl;

import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.impl.RunnableScheduler;
import com.hashnot.xchange.event.AbstractParameterLessMonitor;
import com.hashnot.xchange.event.trade.IOpenOrdersListener;
import com.hashnot.xchange.event.trade.IOpenOrdersMonitor;
import com.hashnot.xchange.event.trade.OpenOrdersEvent;
import com.xeiam.xchange.dto.trade.OpenOrders;

import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class OpenOrdersMonitor extends AbstractParameterLessMonitor<IOpenOrdersListener, OpenOrdersEvent, OpenOrders> implements IOpenOrdersMonitor {
    final private IAsyncExchange exchange;

    public OpenOrdersMonitor(IAsyncExchange exchange, RunnableScheduler scheduler) {
        super(scheduler, IOpenOrdersListener::openOrders, exchange.toString());
        this.exchange = exchange;
    }

    @Override
    protected void getData(Consumer<Future<OpenOrders>> consumer) {
        exchange.getTradeService().getOpenOrders(consumer);
    }

    @Override
    protected OpenOrdersEvent wrap(OpenOrders orders) {
        return new OpenOrdersEvent(exchange.getExchange(), orders);
    }

    @Override
    public void addOpenOrdersListener(IOpenOrdersListener listener) {
        listeners.addListener(listener);
    }

    @Override
    public void removeOpenOrdersListener(IOpenOrdersListener listener) {
        listeners.removeListener(listener);
    }

    @Override
    public String toString() {
        return "OpenOrdersMonitor@" + exchange;
    }
}
