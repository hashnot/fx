package com.hashnot.fx.framework.impl;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.polling.trade.TradeHistoryParams;

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
    public OpenOrders getOpenOrders() throws IOException {
        return backend().getOpenOrders();
    }

    @Override
    public String placeMarketOrder(MarketOrder marketOrder) throws IOException {
        return backend().placeMarketOrder(marketOrder);
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
        return backend().placeLimitOrder(limitOrder);
    }

    @Override
    public boolean cancelOrder(String orderId) throws IOException {
        return backend().cancelOrder(orderId);
    }

    @Override
    public UserTrades getTradeHistory(Object... arguments) throws IOException {
        return backend().getTradeHistory(arguments);
    }

    @Override
    public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
        return backend().getTradeHistory(params);
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
    public TradeHistoryParams createTradeHistoryParams() {
        return backend().createTradeHistoryParams();
    }

    protected PollingTradeService backend() {
        return exchange.get().getPollingTradeService();
    }
}
