package com.hashnot.xchange.event;

/**
 * @author Rafał Krupiński
 */
public interface IOrderBookListener {
    void orderBookChanged(OrderBookUpdateEvent orderBookUpdateEvent);
}
