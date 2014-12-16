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
     * Force update the wallet.
     */
    void update();
}
