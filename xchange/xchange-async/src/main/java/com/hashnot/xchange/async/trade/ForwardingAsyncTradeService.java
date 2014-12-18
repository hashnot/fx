package com.hashnot.xchange.async.trade;

import com.hashnot.xchange.ext.trade.IOrderPlacementListener;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.trade.TradeHistoryParams;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class ForwardingAsyncTradeService implements IAsyncTradeService {
    final private IAsyncTradeService service;

    public ForwardingAsyncTradeService(IAsyncTradeService service) {
        this.service = service;
    }

    @Override
    public void getOpenOrders(Consumer<Future<OpenOrders>> consumer) {
        service.getOpenOrders(consumer);
    }

    @Override
    public void placeMarketOrder(MarketOrder marketOrder, Consumer<Future<String>> consumer) {
        service.placeMarketOrder(marketOrder, consumer);
    }

    @Override
    public Future<String> placeLimitOrder(LimitOrder limitOrder, Consumer<Future<String>> consumer) {
        return service.placeLimitOrder(limitOrder, consumer);
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws ExecutionException, InterruptedException, IOException {
        return service.placeLimitOrder(limitOrder);
    }

    @Override
    public Future<Boolean> cancelOrder(String orderId, Consumer<Future<Boolean>> consumer) {
        return service.cancelOrder(orderId, consumer);
    }

    @Override
    public void getTradeHistory(Consumer<Future<UserTrades>> consumer, Object... arguments) {
        service.getTradeHistory(consumer, arguments);
    }

    @Override
    public void getTradeHistory(TradeHistoryParams params, Consumer<Future<UserTrades>> consumer) {
        service.getTradeHistory(params, consumer);
    }

    @Override
    public TradeHistoryParams createTradeHistoryParams() {
        return service.createTradeHistoryParams();
    }

    @Override
    public void getExchangeSymbols(Consumer<Future<Collection<CurrencyPair>>> consumer) {
        service.getExchangeSymbols(consumer);
    }

    @Override
    public void addLimitOrderPlacedListener(IOrderPlacementListener listener) {
        service.addLimitOrderPlacedListener(listener);
    }

    @Override
    public void removeLimitOrderPlacedListener(IOrderPlacementListener listener) {
        service.removeLimitOrderPlacedListener(listener);
    }
}
