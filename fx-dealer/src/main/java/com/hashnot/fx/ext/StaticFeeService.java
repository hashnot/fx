package com.hashnot.fx.ext;

import com.hashnot.fx.IFeeService;
import com.xeiam.xchange.currency.CurrencyPair;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * @author Rafał Krupiński
 */
public class StaticFeeService implements IFeeService {
    private BigDecimal fee;

    public StaticFeeService(BigDecimal fee) {
        this.fee = fee;
    }

    @Override
    public BigDecimal getFeePercent(CurrencyPair pair) throws IOException {
        return fee;
    }
}
