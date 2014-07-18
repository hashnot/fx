package com.hashnot.fx.spi;

import com.hashnot.fx.spi.ext.IFeeService;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class ExchangeCache {
    final private static Logger log = LoggerFactory.getLogger(ExchangeCache.class);

    // TODO add enum FEE_PRICE FEE_AMOUNT
    final public Map<CurrencyPair, OrderBook> orderBooks = new HashMap<>();
    final public Map<String, BigDecimal> wallet = new HashMap<>();
    final public Map<String, BigDecimal> orderBookLimits = new HashMap<>();
    final public IFeeService feeService;

    final public String name;

    public ExchangeCache(String name, IFeeService feeService) {
        this.name = name;
        this.feeService = feeService;
    }

    public void updateWallet(String currency, BigDecimal amount) {
        BigDecimal currentAmount = wallet.getOrDefault(currency, BigDecimal.ZERO);
        wallet.put(currency, currentAmount.add(amount));
    }

    public void verifyWallet() {
        boolean negative = false;
        log.info("-- {} --", name);
        for (Map.Entry<String, BigDecimal> e : wallet.entrySet()) {
            log.info("\t{} = {}", e.getKey(), e.getValue());
            negative |= e.getValue().signum() < 0;
        }
        if (negative)
            throw new IllegalStateException("Negative amount in wallet");
    }

}
