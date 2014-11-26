package com.hashnot.xchange.event.trade;

/**
 * @author Rafał Krupiński
 */
public interface IUserTradeMonitor {
    void addTradeListener(IUserTradeListener orderListener);

    void removeTradeListener(IUserTradeListener orderListener);
}
