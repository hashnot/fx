package com.hashnot.fx.xchange.ext;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.ExchangeSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Rafał Krupiński
 */
public class KrakenMultiKeyExchange extends AbstractExchange {
    private List<Exchange> exchanges;

    private final AtomicInteger keyCounter = new AtomicInteger(0);

    public KrakenMultiKeyExchange(List<ExchangeSpecification> exchanges) {
        this.exchanges = new ArrayList<>(exchanges.size());
        for (ExchangeSpecification spec : exchanges)
            this.exchanges.add(ExchangeFactory.INSTANCE.createExchange(spec));
    }

    @Override
    protected Exchange getExchange() {
        return getExchange(true);
    }

    @Override
    protected Exchange getExchange(boolean forRemote) {
        int counter = forRemote ? keyCounter.incrementAndGet() : keyCounter.get();
        return exchanges.get(counter % exchanges.size());
    }

    @Override
    public void applySpecification(ExchangeSpecification exchangeSpecification) {
        for (Exchange exchange : exchanges)
            exchange.applySpecification(exchangeSpecification);
    }

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
        return getExchange(false).getDefaultExchangeSpecification();
    }

    @Override
    public ExchangeSpecification getExchangeSpecification() {
        return getExchange(false).getExchangeSpecification();
    }
}
