package com.hashnot.xchange.async.impl;

import java.util.Collection;

/**
 * @author Rafał Krupiński
 */
public interface RoundRobinSchedulerMBean {
    Collection<String> getPriority();

    Collection<String> getTasks();
}
