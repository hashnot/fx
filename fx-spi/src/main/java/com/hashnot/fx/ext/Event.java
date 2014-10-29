package com.hashnot.fx.ext;

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
