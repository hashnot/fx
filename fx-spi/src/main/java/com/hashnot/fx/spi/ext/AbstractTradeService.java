package com.hashnot.fx.spi.ext;

import com.google.common.base.Supplier;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.service.polling.PollingTradeService;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractTradeService implements PollingTradeService {
    final private Supplier<PollingTradeService> backend;

    public AbstractTradeService(Supplier<PollingTradeService> backend) {
        this.backend = backend;
    }

    @Override
    public OpenOrders getOpenOrders() throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend.get().getOpenOrders();
    }

    @Override
    public String placeMarketOrder(MarketOrder marketOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend.get().placeMarketOrder(marketOrder);
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend.get().placeLimitOrder(limitOrder);
    }

    @Override
    public boolean cancelOrder(String orderId) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend.get().cancelOrder(orderId);
    }

    @Override
    public Trades getTradeHistory(Object... arguments) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend.get().getTradeHistory(arguments);
    }

    @Override
    public Collection<CurrencyPair> getExchangeSymbols() throws IOException {
        return backend.get().getExchangeSymbols();
    }
}
