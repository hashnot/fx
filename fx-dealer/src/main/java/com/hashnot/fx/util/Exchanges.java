package com.hashnot.fx.util;

import com.hashnot.xchange.event.IExchangeMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class Exchanges {
    final private static Logger log = LoggerFactory.getLogger(Exchanges.class);

    public static void report(Collection<IExchangeMonitor> context) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (IExchangeMonitor e : context) {
            log.info("{}", e);
            for (Map.Entry<String, BigDecimal> f : e.getWalletMonitor().getWallet().entrySet()) {
                String currency = f.getKey();
                BigDecimal amount = f.getValue();
                log.info("{} = {}", currency, amount);
                BigDecimal current = totals.getOrDefault(currency, ZERO);
                totals.put(currency, current.add(amount));
            }
        }
        log.info("=== Totals ===");
        for (Map.Entry<String, BigDecimal> e : totals.entrySet())
            log.info("{} = {}", e.getKey(), e.getValue());
        log.info("=== End ===");
    }
}
