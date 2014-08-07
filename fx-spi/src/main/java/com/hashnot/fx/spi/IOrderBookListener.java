package com.hashnot.fx.spi;

import com.hashnot.fx.OrderBookUpdateEvent;

/**
 * @author Rafał Krupiński
 */
public interface IOrderBookListener {
    void changed(OrderBookUpdateEvent orderBookUpdateEvent);
}
