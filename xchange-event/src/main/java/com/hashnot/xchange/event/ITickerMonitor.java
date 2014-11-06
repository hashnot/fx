package com.hashnot.xchange.event;

import com.hashnot.xchange.ext.Market;
import com.xeiam.xchange.currency.CurrencyPair;

/**
 * @author Rafał Krupiński
 */
public interface ITickerMonitor {
    void addTickerListener(ITickerListener listener, CurrencyPair pair);

    void removeTickerListener(ITickerListener listener, CurrencyPair pair);
}
