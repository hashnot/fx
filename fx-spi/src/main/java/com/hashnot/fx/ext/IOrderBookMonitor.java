package com.hashnot.fx.ext;

import com.xeiam.xchange.currency.CurrencyPair;

/**
 * @author Rafał Krupiński
 */
public interface IOrderBookMonitor {
    void addOrderBookListener(CurrencyPair pair, IOrderBookListener listener);

    void removeOrderBookListener(CurrencyPair pair, IOrderBookListener listener);
}
