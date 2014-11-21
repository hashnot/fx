package com.hashnot.xchange.event.trade;

/**
 * @author Rafał Krupiński
 */
public interface IUserTradesMonitor {
    void addTradesListener(IUserTradesListener listener);

    void removeTradesListener(IUserTradesListener listener);
}
