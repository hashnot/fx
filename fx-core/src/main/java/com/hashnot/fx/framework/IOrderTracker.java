package com.hashnot.fx.framework;

import com.xeiam.xchange.dto.trade.LimitOrder;

import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public interface IOrderTracker {
    Map<String, LimitOrder> getMonitored();

    Map<String, LimitOrder> getCurrent();
}
