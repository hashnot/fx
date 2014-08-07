package com.hashnot.fx.spi.ext;

import com.hashnot.fx.util.SchedulerExecutorFactory;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.ExchangeSpecification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Rafał Krupiński
 */
public class KrakenMultiKeyExchange extends AbstractExchange {
    private List<Exchange> exchanges;

    private final AtomicInteger keyCounter = new AtomicInteger(0);

    public KrakenMultiKeyExchange(List<ExchangeSpecification> exchanges, IFeeService feeService, ScheduledExecutorService executor, Map<String, BigDecimal> walletUnit, Map<String, BigDecimal> minimumOrder, int limitPriceScale, int tradeAmountScale) {
        super(feeService, new SchedulerExecutorFactory(executor, 200), walletUnit, minimumOrder, limitPriceScale, tradeAmountScale);
        this.exchanges = new ArrayList<>(exchanges.size());
        for (ExchangeSpecification spec : exchanges)
            this.exchanges.add(ExchangeFactory.INSTANCE.createExchange(spec));
    }

    @Override
    protected Exchange getExchange() {
        return exchanges.get((keyCounter.incrementAndGet() % exchanges.size()));
    }

}
