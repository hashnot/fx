package com.hashnot.fx.framework;

/**
 * @author Rafał Krupiński
 */
public interface IOrderBookSideListener {
    void orderBookSideChanged(OrderBookSideUpdateEvent evt);
}
