package com.hashnot.fx.ext;

/**
 * @author Rafał Krupiński
 */
public interface ITickerMonitor {
    void addTickerListener(ITickerListener listener, Market market);

    void removeTickerListener(ITickerListener listener, Market market);
}
