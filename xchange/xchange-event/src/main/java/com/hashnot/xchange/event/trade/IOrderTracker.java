package com.hashnot.xchange.event.trade;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public interface IOrderTracker extends IUserTradeMonitor {
    Map<String, LimitOrder> getMonitored(Exchange exchange);

    Map<String, LimitOrder> getCurrent(Exchange exchange);
}
