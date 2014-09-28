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
import com.xeiam.xchange.service.polling.PollingTradeService;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Rafał Krupiński
 */
public class NotifyingTradeService implements ITradeService {
    private PollingTradeService backend;

    private Set<ILimitOrderPlacementListener> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public NotifyingTradeService(PollingTradeService backend) {
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
        String id = backend.placeLimitOrder(limitOrder);
        for (ILimitOrderPlacementListener listener : listeners)
            listener.limitOrderPlaced(limitOrder, id);
        return id;
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
    public void removeLimitOrderPlacedListener(ILimitOrderPlacementListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void addLimitOrderPlacedListener(ILimitOrderPlacementListener listener) {
        listeners.add(listener);
    }

    @Override
    public Collection<CurrencyPair> getExchangeSymbols() throws IOException {
        return backend.getExchangeSymbols();
    }
}
