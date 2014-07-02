package com.hashnot.fx;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * @author Rafał Krupiński
 */
public interface IFeeService {
    BigDecimal getFeePercent(CurrencyPair pair) throws IOException;
}
