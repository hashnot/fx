package com.hashnot.fx.framework.impl;

import com.hashnot.xchange.event.ParametrizedMonitorMBean;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public interface BestOfferMonitorMBean extends ParametrizedMonitorMBean {
    Map<String, BigDecimal> getBestPrices();
}
