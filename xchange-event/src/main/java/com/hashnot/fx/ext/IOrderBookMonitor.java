package com.hashnot.fx.ext;

/**
 * @author Rafał Krupiński
 */
public interface IOrderBookMonitor {
    void addOrderBookListener(IOrderBookListener listener, Market market);

    void removeOrderBookListener(IOrderBookListener listener, Market market);
}
