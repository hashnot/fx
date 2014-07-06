package com.hashnot.fx.framework;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author Rafał Krupiński
 */
public class CacheUpdateEvent {
    final public Exchange exchange;
    final public Collection<CurrencyPair> pairs;

    public CacheUpdateEvent(Exchange exchange, Collection<CurrencyPair> pairs) {
        this.exchange = exchange;
        this.pairs = new HashSet<>(pairs);
    }
}
