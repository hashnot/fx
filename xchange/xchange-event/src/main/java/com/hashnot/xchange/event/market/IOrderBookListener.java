package com.hashnot.xchange.event.market;

/**
 * @author Rafał Krupiński
 */
public interface IOrderBookListener {
    void orderBookChanged(OrderBookUpdateEvent orderBookUpdateEvent);
}
