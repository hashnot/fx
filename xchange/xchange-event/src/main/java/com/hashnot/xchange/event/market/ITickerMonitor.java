package com.hashnot.xchange.event.market;

import com.xeiam.xchange.currency.CurrencyPair;

/**
 * @author Rafał Krupiński
 */
public interface ITickerMonitor {
    void addTickerListener(ITickerListener listener, CurrencyPair pair);

    void removeTickerListener(ITickerListener listener, CurrencyPair pair);
}
