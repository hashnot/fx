package com.hashnot.fx.ext;

/**
 * @author Rafał Krupiński
 */
public interface IOrderBookListener {
    void orderBookChanged(OrderBookUpdateEvent orderBookUpdateEvent);
}
