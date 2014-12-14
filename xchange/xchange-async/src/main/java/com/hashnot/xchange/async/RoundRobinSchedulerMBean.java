package com.hashnot.xchange.async;

import java.util.Collection;

/**
 * @author Rafał Krupiński
 */
public interface RoundRobinSchedulerMBean {
    Collection<String> getPriority();

    Collection<String> getTasks();
}
