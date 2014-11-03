package com.hashnot.xchange.event;

/**
 * @author Rafał Krupiński
 */
public interface ITradesMonitor {
    void addTradesListener(ITradesListener listener);

    void removeTradesListener(ITradesListener listener);
}
