package com.hashnot.fx.ext.impl;

import com.hashnot.fx.spi.ext.IFeeService;
import com.xeiam.xchange.currency.CurrencyPair;

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
    public BigDecimal getFeePercent(CurrencyPair pair) {
        return fee;
    }
}
