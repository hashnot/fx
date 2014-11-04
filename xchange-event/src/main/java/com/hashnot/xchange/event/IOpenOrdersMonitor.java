package com.hashnot.xchange.event;

/**
 * @author Rafał Krupiński
 */
public interface IOpenOrdersMonitor {
    void addOpenOrdersListener(IOpenOrdersListener openOrdersListener);

    void removeOpenOrdersListener(IOpenOrdersListener openOrdersListener);
}
