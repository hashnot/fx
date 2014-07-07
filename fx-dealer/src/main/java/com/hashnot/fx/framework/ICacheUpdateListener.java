package com.hashnot.fx.framework;

import com.xeiam.xchange.currency.CurrencyPair;

import java.util.Collection;

/**
 * @author Rafał Krupiński
 */
public interface ICacheUpdateListener {
    void cacheUpdated(Collection<CurrencyPair> pairs);
}
