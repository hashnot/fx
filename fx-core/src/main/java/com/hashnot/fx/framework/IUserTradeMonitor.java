package com.hashnot.fx.framework;

import com.xeiam.xchange.Exchange;

/**
 * @author Rafał Krupiński
 */
public interface IUserTradeMonitor {
    void addTradeListener(IUserTradeListener orderListener, Exchange exchange);

    void removeTradeListener(IUserTradeListener orderListener, Exchange exchange);
}
