package com.hashnot.xchange.event.trade;

/**
 * @author Rafał Krupiński
 */
public interface IOpenOrdersListener {
    void openOrders(OpenOrdersEvent openOrdersEvent);
}
