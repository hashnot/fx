package com.hashnot.fx.ext;

import com.hashnot.fx.IFeeService;
import com.xeiam.xchange.currency.Currencies;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class BitcurextFeeService implements IFeeService {
    private Map<String, BigDecimal> fees = new HashMap<>();

    {
        fees.put(Currencies.PLN, new BigDecimal(".005"));
        fees.put(Currencies.EUR, new BigDecimal(".004"));
    }

    @Override
    public BigDecimal getFeePercent(CurrencyPair pair) throws IOException {
        return fees.get(pair.counterSymbol);
    }
}
