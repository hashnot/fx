package com.hashnot.xchange.ext.event;

/**
 * @author Rafał Krupiński
 */
public abstract class Event<ST> {
    public final ST source;

    public Event(ST source) {
        assert source != null : "null source";
        this.source = source;
    }
}
