package com.hashnot.xchange.event.trade.impl;

import com.hashnot.xchange.event.ParameterlessMonitorMBean;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public interface OrderTrackerMBean extends ParameterlessMonitorMBean {
    Map<String,BigDecimal> getOrderIds();
}
