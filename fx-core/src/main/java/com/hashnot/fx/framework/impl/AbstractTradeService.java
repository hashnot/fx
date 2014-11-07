package com.hashnot.fx.framework.impl;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.PollingTradeService;

import java.io.IOException;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractTradeService implements PollingTradeService {
    final protected Supplier<Exchange> exchange;

    protected AbstractTradeService(Supplier<Exchange> exchange) {
        this.exchange = exchange;
    }

    @Override
    public OpenOrders getOpenOrders() throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend().getOpenOrders();
    }

    @Override
    public String placeMarketOrder(MarketOrder marketOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend().placeMarketOrder(marketOrder);
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend().placeLimitOrder(limitOrder);
    }

    @Override
    public boolean cancelOrder(String orderId) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend().cancelOrder(orderId);
    }

    @Override
    public UserTrades getTradeHistory(Object... arguments) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend().getTradeHistory(arguments);
    }

    @Override
    public Collection<CurrencyPair> getExchangeSymbols() throws IOException {
        return backend().getExchangeSymbols();
    }

    @Override
    public String toString() {
        return backend().toString();
    }

    @Override
    public Object createTradeHistoryParams() {
        return backend().createTradeHistoryParams();
    }

    protected PollingTradeService backend() {
        return exchange.get().getPollingTradeService();
    }
}
