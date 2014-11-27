package com.hashnot.xchange.event.account;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public interface IWalletMonitor {
    BigDecimal getWallet(String currency);

    Map<String, BigDecimal> getWallet();

    /**
     * Force update the wallet, and call all the listeners, including ad-hoc passed as the parameter.
     *
     * @param listeners list of ad-hoc listeners
     */
    void update(Iterable<IWalletListener> listeners);

    void addWalletListener(IWalletListener listener, String currency);

    void removeWalletListener(IWalletListener listener, String currency);
}
