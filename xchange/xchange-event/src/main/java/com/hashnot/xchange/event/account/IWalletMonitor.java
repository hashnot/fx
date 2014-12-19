package com.hashnot.xchange.event.account;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author Rafał Krupiński
 */
public interface IWalletMonitor {
    BigDecimal getWallet(String currency);

    Map<String, BigDecimal> getWallet();

    /**
     * Force update the wallet (asynchronous).
     */
    Future<Map<String, BigDecimal>> update();
}
