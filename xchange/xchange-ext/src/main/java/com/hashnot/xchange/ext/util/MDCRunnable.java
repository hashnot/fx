package com.hashnot.xchange.ext.util;

import org.slf4j.MDC;

import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class MDCRunnable implements Runnable {
    final private Runnable run;
    private final Map<String, String> mdc;

    public MDCRunnable(Runnable run) {
        this.run = run;
        mdc = MDC.getCopyOfContextMap();
    }

    @Override
    public void run() {
        try {
            if (mdc != null)
                MDC.setContextMap(mdc);
            run.run();
        } finally {
            MDC.clear();
        }
    }

    @Override
    public String toString() {
        return "MDC+" + run.toString();
    }
}
