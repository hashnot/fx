package com.hashnot.xchange.async.impl;

/**
* @author Rafał Krupiński
*/
public interface Callable<V, E extends Exception> {
    V call() throws E;
}
