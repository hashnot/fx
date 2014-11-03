package com.hashnot.xchange.event;

import com.hashnot.xchange.ext.Market;

/**
 * @author Rafał Krupiński
 */
public interface ITickerMonitor {
    void addTickerListener(ITickerListener listener, Market market);

    void removeTickerListener(ITickerListener listener, Market market);
}
