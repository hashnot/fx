package com.hashnot.xchange.event.market;

import com.xeiam.xchange.currency.CurrencyPair;

/**
 * @author Rafał Krupiński
 */
public interface IOrderBookMonitor {
    void addOrderBookListener(IOrderBookListener listener, CurrencyPair pair);

    void removeOrderBookListener(IOrderBookListener listener, CurrencyPair pair);
}
