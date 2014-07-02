package com.hashnot.fx.ext;

import com.hashnot.fx.IFeeService;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.kraken.Kraken;
import com.xeiam.xchange.kraken.dto.account.KrakenTradeVolume;
import com.xeiam.xchange.kraken.service.polling.KrakenAccountService;
import com.xeiam.xchange.kraken.service.polling.KrakenBasePollingService;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * @author Rafał Krupiński
 */
public class KrakenFeeService<T extends Kraken> extends KrakenBasePollingService<T> implements IFeeService {
    private KrakenAccountService accountService;

    public KrakenFeeService(Class<T> type, ExchangeSpecification exchangeSpecification, KrakenAccountService accountService) {
        super(type, exchangeSpecification);
        this.accountService = accountService;
    }

    @Override
    public BigDecimal getFeePercent(CurrencyPair pair) throws IOException {
        KrakenTradeVolume tradeVolume = accountService.getTradeVolume(pair);
        return tradeVolume.getFees().get(createKrakenCurrencyPair(pair)).getFee();
    }
}
