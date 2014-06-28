package com.hashnot.fx.util;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.ExchangeInfo;
import com.xeiam.xchange.service.BaseExchangeService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Rafał Krupiński
 */
public class ExchangeUtil {
    public static Collection<CurrencyPair> getCurrencyPairs(PollingMarketDataService service) throws IOException {
        assert service != null;
        if (service instanceof BaseExchangeService)
            return ((BaseExchangeService) service).getExchangeSymbols();
        else {
            ExchangeInfo exchangeInfo = service.getExchangeInfo();
            return exchangeInfo.getPairs();
        }
    }
}
