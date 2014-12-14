package com.hashnot.xchange.event.account;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public interface WalletMonitorMBean {
    Map<String, BigDecimal> getWallet();
}
