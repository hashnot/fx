package com.hashnot.fx.ext;

import com.hashnot.fx.spi.ext.IFeeService;
import com.xeiam.xchange.currency.CurrencyPair;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class BTCEFeeService implements IFeeService {
    private Map<CurrencyPair, BigDecimal> fees = new HashMap<>();
    private BigDecimal defaultFee = new BigDecimal(".002");

    {
        fees.put(CurrencyPair.BTC_RUB, new BigDecimal(".005"));
    }

    @Override
    public BigDecimal getFeePercent(CurrencyPair pair) {
        BigDecimal result = fees.get(pair);
        return result != null ? result : defaultFee;
    }
}
