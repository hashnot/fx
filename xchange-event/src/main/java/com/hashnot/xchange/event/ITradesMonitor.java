package com.hashnot.xchange.event;

/**
 * @author Rafał Krupiński
 */
public interface ITradesMonitor {
    void addTradesListener(IUserTradesListener listener);

    void removeTradesListener(IUserTradesListener listener);
}
