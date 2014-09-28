package com.hashnot.fx.ext;

/**
 * @author Rafał Krupiński
 */
public interface ITradesMonitor {
    void addTradesListener(ITradesListener listener);

    void removeTradesListener(ITradesListener listener);
}
