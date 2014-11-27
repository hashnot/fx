package com.hashnot.fx.framework;

import com.hashnot.xchange.event.IExchangeMonitor;
import groovy.lang.GroovyShell;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Rafał Krupiński
 */
public class Setup {
    @SuppressWarnings("unchecked")
    public static Collection<IExchangeMonitor> load(String path, ScheduledExecutorService scheduler) {
        GroovyShell sh = new GroovyShell();
        sh.setVariable("executor", scheduler);
        try {
            return (Collection<IExchangeMonitor>) sh.evaluate(new File(path));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load setup script", e);
        }
    }
}
