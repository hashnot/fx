package com.hashnot.fx.spi.ext;

import com.hashnot.fx.spi.ITradeListener;

/**
 * @author Rafał Krupiński
 */
public interface ITradeMonitor {
    void addTradeListener(ITradeListener orderListener);

    void removeTradeListener(ITradeListener orderListener);
}
