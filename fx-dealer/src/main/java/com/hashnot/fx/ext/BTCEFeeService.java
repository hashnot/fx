package com.hashnot.fx.ext;

import com.hashnot.fx.IFeeService;
import com.xeiam.xchange.currency.CurrencyPair;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class BTCEFeeService implements IFeeService {
    Map<CurrencyPair, BigDecimal> fees = new HashMap<>();
    BigDecimal defaultFee = new BigDecimal(".002");

    {
        fees.put(CurrencyPair.BTC_RUB, new BigDecimal(".005"));
    }

    @Override
    public BigDecimal getFeePercent(CurrencyPair pair) throws IOException {
        BigDecimal result = fees.get(pair);
        return result != null ? result : defaultFee;
    }
}
