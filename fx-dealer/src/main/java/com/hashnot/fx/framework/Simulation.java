package com.hashnot.fx.framework;

import com.hashnot.fx.util.CurrencyPairUtil;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class Simulation {
    final private static Logger log = LoggerFactory.getLogger(Simulation.class);
    private Map<Exchange, Map<String, BigDecimal>> wallet = new HashMap<>();

    public void close(LimitOrder order, Exchange x) {
        add(CurrencyPairUtil.reverse(order), x);
        report();
    }

    public void open(LimitOrder order, Exchange x) {
        add(order, x);
        report();
    }

    public void add(LimitOrder order, Exchange x) {
        BigDecimal amount = order.getTradableAmount().divide(order.getLimitPrice(), CurrencyPairUtil.c);
        add(x, order.getCurrencyPair().baseSymbol, amount.negate());

        add(x, order.getCurrencyPair().counterSymbol, order.getTradableAmount());
    }

    public void add(Exchange x, String currency, BigDecimal amount) {
        Map<String, BigDecimal> account = wallet.get(x);
        if (account == null) {
            account = new HashMap<>();
            wallet.put(x, account);
        }

        BigDecimal currentAmount = account.getOrDefault(currency, BigDecimal.ZERO);
        account.put(currency, currentAmount.add(amount));
    }

    public void report() {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (Map.Entry<Exchange, Map<String, BigDecimal>> e : wallet.entrySet()) {
            log.info("{}", e.getKey().getClass().getSimpleName());
            for (Map.Entry<String, BigDecimal> f : e.getValue().entrySet()) {
                String currency = f.getKey();
                BigDecimal amount = f.getValue();
                log.info("{} = {}", currency, amount);
                BigDecimal current = totals.getOrDefault(currency, BigDecimal.ZERO);
                totals.put(currency, current.add(amount));
            }
        }
        log.info("=== Totals ===");
        for (Map.Entry<String, BigDecimal> e : totals.entrySet())
            log.info("{} = {}", e.getKey(), e.getValue());
        log.info("=== End ===");
    }
}
