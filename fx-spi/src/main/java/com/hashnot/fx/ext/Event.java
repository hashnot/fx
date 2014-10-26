package com.hashnot.fx.ext;

/**
 * @author Rafał Krupiński
 */
public class Event<ST> {
    public final ST source;

    public Event(ST source) {
        assert source != null : "null source";
        this.source = source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Event)) return false;

        Event event = (Event) o;

        if (!source.equals(event.source)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return source.hashCode();
    }
}
