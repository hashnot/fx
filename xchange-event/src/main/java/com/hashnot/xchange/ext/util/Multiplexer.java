package com.hashnot.xchange.ext.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * @author Rafał Krupiński
 */
public class Multiplexer {
    final private static Logger log = LoggerFactory.getLogger(Multiplexer.class);

    public static <E, L> void multiplex(Iterable<L> listeners, E event, BiConsumer<L, E> call) {
        for (L listener : listeners) {
            try {
                call.accept(listener, event);
            } catch (RuntimeException x) {
                log.warn("Error from {}", listener, x);
            } catch (Throwable x) {
                log.warn("Error from {}", listener, x);
                throw x;
            }
        }
    }
}
