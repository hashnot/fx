package com.hashnot.fx.kokos;

import com.xeiam.xchange.BaseExchange;
import com.xeiam.xchange.ExchangeSpecification;

/**
 * @author Rafał Krupiński
 */
public class KokosExchange extends BaseExchange {

    @Override
    public void applySpecification(ExchangeSpecification exchangeSpecification) {
        super.applySpecification(exchangeSpecification);

        pollingMarketDataService = new KokosMarketDataService();
    }

    @Override
    public ExchangeSpecification getDefaultExchangeSpecification() {
        return null;
    }
}
