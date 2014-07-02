package com.hashnot.fx.ext;

import com.hashnot.fx.IFeeService;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * @author Rafał Krupiński
 */
public class KrakenFeeServiceStub implements IFeeService {
    private static final BigDecimal FEE = new BigDecimal(".002");

    @Override
    public BigDecimal getFeePercent(CurrencyPair pair) throws IOException {
        return FEE;
    }
}
