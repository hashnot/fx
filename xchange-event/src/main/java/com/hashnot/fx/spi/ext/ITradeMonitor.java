package com.hashnot.fx.spi.ext;

import com.hashnot.fx.ext.ITradeListener;

/**
 * @author Rafał Krupiński
 */
public interface ITradeMonitor {
    void addTradeListener(ITradeListener orderListener);

    void removeTradeListener(ITradeListener orderListener);
}
