package com.hashnot.fx.spi.ext;

import com.hashnot.fx.util.exec.IExecutorStrategyFactory;
import com.xeiam.xchange.Exchange;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class SimpleExchange extends AbstractExchange {

    private final Exchange exchange;

    public SimpleExchange(Exchange exchange, IFeeService feeService, IExecutorStrategyFactory executorStrategyFactory, Map<String, BigDecimal> walletUnit, Map<String, BigDecimal> minimumOrder, int limitPriceScale, int tradeAmountScale) {
        super(feeService, executorStrategyFactory, walletUnit, minimumOrder, limitPriceScale, tradeAmountScale);
        this.exchange = exchange;
    }

    @Override
    protected Exchange getExchange() {
        return exchange;
    }
}
