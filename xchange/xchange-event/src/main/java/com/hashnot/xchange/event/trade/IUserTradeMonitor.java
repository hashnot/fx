package com.hashnot.xchange.event.trade;

import com.xeiam.xchange.Exchange;

/**
 * @author Rafał Krupiński
 */
public interface IUserTradeMonitor {
    void addTradeListener(IUserTradeListener orderListener, Exchange exchange);

    void removeTradeListener(IUserTradeListener orderListener, Exchange exchange);
}
