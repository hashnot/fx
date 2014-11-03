package com.hashnot.fx.spi.ext;

import com.hashnot.fx.spi.ILimitOrderPlacementListener;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractTradeService implements ITradeService {
    final private ITradeService backend;

    protected AbstractTradeService(ITradeService backend) {
        this.backend = backend;
    }

    @Override
    public OpenOrders getOpenOrders() throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend.getOpenOrders();
    }

    @Override
    public String placeMarketOrder(MarketOrder marketOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend.placeMarketOrder(marketOrder);
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend.placeLimitOrder(limitOrder);
    }

    @Override
    public boolean cancelOrder(String orderId) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend.cancelOrder(orderId);
    }

    @Override
    public Trades getTradeHistory(Object... arguments) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        return backend.getTradeHistory(arguments);
    }

    @Override
    public Collection<CurrencyPair> getExchangeSymbols() throws IOException {
        return backend.getExchangeSymbols();
    }

    @Override
    public void removeLimitOrderPlacedListener(ILimitOrderPlacementListener listener) {
        backend.removeLimitOrderPlacedListener(listener);
    }

    @Override
    public void addLimitOrderPlacedListener(ILimitOrderPlacementListener listener) {
        backend.addLimitOrderPlacedListener(listener);
    }

}
