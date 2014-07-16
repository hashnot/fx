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
    // TODO add enum FEE_PRICE FEE_AMOUNT
    final public Map<CurrencyPair, OrderBook> orderBooks = new HashMap<>();
    final public Map<String, BigDecimal> wallet = new HashMap<>();
    final public Map<String, BigDecimal> orderBookLimits = new HashMap<>();
    final public IFeeService feeService;

    public ExchangeCache(IFeeService feeService) {
        this.feeService = feeService;
    }

    public void updateWallet(String currency, BigDecimal amount) {
        BigDecimal currentAmount = wallet.getOrDefault(currency, BigDecimal.ZERO);
        wallet.put(currency, currentAmount.add(amount));
    }

}
