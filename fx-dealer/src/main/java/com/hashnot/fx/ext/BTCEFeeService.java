package com.hashnot.fx.ext;

import com.xeiam.xchange.currency.CurrencyPair;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class BTCEFeeService {
    private Map<CurrencyPair, BigDecimal> fees = new HashMap<>();
    private BigDecimal defaultFee = new BigDecimal(".002");

    {
        fees.put(CurrencyPair.BTC_RUB, new BigDecimal(".005"));
    }

    public BigDecimal getFeePercent(CurrencyPair pair) {
        BigDecimal result = fees.get(pair);
        return result != null ? result : defaultFee;
    }
}
