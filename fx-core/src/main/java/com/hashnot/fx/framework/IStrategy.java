package com.hashnot.fx.framework;

import com.hashnot.xchange.event.IExchangeMonitor;
import com.xeiam.xchange.currency.CurrencyPair;

import java.util.Collection;

/**
 * @author Rafał Krupiński
 */
public interface IStrategy {
    void init(Collection<IExchangeMonitor> exchangeMonitors, Collection<CurrencyPair> pairs) throws Exception;

    void destroy();
}
