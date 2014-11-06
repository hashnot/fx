package com.hashnot.fx.framework;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public interface IOrderTracker {
    Map<String, LimitOrder> getMonitored(Exchange exchange);

    Map<String, LimitOrder> getCurrent(Exchange exchange);

    void addTradeListener(IUserTradeListener listener, Exchange source);

    void removeTradeListener(IUserTradeListener listener, Exchange source);
}
