package com.hashnot.fx.framework;

/**
 * @author Rafał Krupiński
 */
public interface IUserTradeMonitor {
    void addTradeListener(IUserTradeListener orderListener);

    void removeTradeListener(IUserTradeListener orderListener);
}
