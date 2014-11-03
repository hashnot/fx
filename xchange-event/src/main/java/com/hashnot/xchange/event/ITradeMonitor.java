package com.hashnot.xchange.event;

/**
 * @author Rafał Krupiński
 */
public interface ITradeMonitor {
    void addTradeListener(ITradeListener orderListener);

    void removeTradeListener(ITradeListener orderListener);
}
