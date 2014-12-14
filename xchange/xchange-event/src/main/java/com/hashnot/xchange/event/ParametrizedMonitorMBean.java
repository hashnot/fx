package com.hashnot.xchange.event;

import java.util.Map;

/**
 * Internal JMX interface
 *
 * @author Rafał Krupiński
 */
public interface ParametrizedMonitorMBean {
    Map<String, String> getListeners();
}
