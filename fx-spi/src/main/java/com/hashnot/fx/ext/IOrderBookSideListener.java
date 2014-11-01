package com.hashnot.fx.ext;

/**
 * @author Rafał Krupiński
 */
public interface IOrderBookSideListener {
    void orderBookSideChanged(OrderBookSideUpdateEvent evt);
}
