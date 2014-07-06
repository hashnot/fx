package com.hashnot.fx.spi.ext;

import com.xeiam.xchange.currency.CurrencyPair;

import java.math.BigDecimal;

/**
 * @author Rafał Krupiński
 */
public interface IFeeService {
    BigDecimal getFeePercent(CurrencyPair pair);
}
