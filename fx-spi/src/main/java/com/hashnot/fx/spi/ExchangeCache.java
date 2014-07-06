package com.hashnot.fx.spi;

import com.hashnot.fx.spi.ext.IFeeService;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class ExchangeCache {
    final public Map<CurrencyPair, OrderBook> orderBooks = new HashMap<>();
    final public Map<String, BigDecimal> wallet = new HashMap<>();
    final public Map<String, BigDecimal> orderBookLimits = new HashMap<>();
    final public IFeeService feeService;

    public ExchangeCache(IFeeService feeService) {
        this.feeService = feeService;
    }
}
