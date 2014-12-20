package com.hashnot.xchange.ext.util;

import org.slf4j.MDC;

import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class MDCRunnable implements Runnable {
    final private Runnable run;
    final private Map<String, String> mdc;

    public MDCRunnable(Runnable run) {
        this(run, MDC.getCopyOfContextMap());
    }

    public MDCRunnable(Runnable run, Map<String, String> mdc) {
        assert run != null;
        this.run = run;
        this.mdc = mdc;
    }

    @Override
    public void run() {
        try {
            if (mdc != null)
                MDC.setContextMap(mdc);
            run.run();
        } finally {
            if (mdc != null)
                MDC.clear();
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o != null && o instanceof MDCRunnable && equals((MDCRunnable) o);
    }

    private boolean equals(MDCRunnable that) {
        return run.equals(that.run);
    }

    @Override
    public int hashCode() {
        return run.hashCode();
    }

    @Override
    public String toString() {
        return "MDC+" + run.toString();
    }
}
