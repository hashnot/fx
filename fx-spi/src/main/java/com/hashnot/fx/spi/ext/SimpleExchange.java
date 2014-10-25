package com.hashnot.fx.spi.ext;

import com.hashnot.fx.util.exec.IExecutorStrategyFactory;
import com.xeiam.xchange.Exchange;

/**
 * @author Rafał Krupiński
 */
public class SimpleExchange extends AbstractExchange {

    private final Exchange exchange;

    public SimpleExchange(Exchange exchange, IExecutorStrategyFactory executorStrategyFactory) {
        super(executorStrategyFactory);
        this.exchange = exchange;
    }

    @Override
    protected Exchange getExchange() {
        return exchange;
    }
}
