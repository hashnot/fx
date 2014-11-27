package com.hashnot.xchange.async.trade;

import com.google.common.util.concurrent.SettableFuture;
import com.hashnot.xchange.async.AbstractAsyncService;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.polling.trade.TradeHistoryParams;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class AsyncTradeService extends AbstractAsyncService implements IAsyncTradeService {
    final private PollingTradeService service;

    public AsyncTradeService(Executor executor, PollingTradeService pollingTradeService) {
        super(executor);
        service = pollingTradeService;
    }

    @Override
    public void getOpenOrders(Consumer<Future<OpenOrders>> consumer) {
        call(service::getOpenOrders, consumer);
    }

    @Override
    public void placeMarketOrder(MarketOrder marketOrder, Consumer<Future<String>> consumer) {
        call(() -> service.placeMarketOrder(marketOrder), consumer);
    }

    @Override
    public void placeLimitOrder(LimitOrder limitOrder, Consumer<Future<String>> consumer) {
        call(() -> service.placeLimitOrder(limitOrder), consumer);
    }

    @Override
    public void cancelOrder(String orderId, Consumer<Future<Boolean>> consumer) {
        call(() -> service.cancelOrder(orderId), consumer);
    }

    @Override
    public void getTradeHistory(Consumer<Future<UserTrades>> consumer, Object... arguments) {
        call(() -> service.getTradeHistory(arguments), consumer);
    }

    @Override
    public void getTradeHistory(TradeHistoryParams params, Consumer<Future<UserTrades>> consumer) {
        call(() -> service.getTradeHistory(params), consumer);
    }

    @Override
    public TradeHistoryParams createTradeHistoryParams() {
        return service.createTradeHistoryParams();
    }

    @Override
    public void getExchangeSymbols(Consumer<Future<Collection<CurrencyPair>>> consumer) {
        call(service::getExchangeSymbols, consumer);
    }

    /**
     * Update existing LimitOrder with a new one
     *
     * @param orderId  orderOd of the existing order
     * @param order    new order
     * @param consumer callback to be notified of the new order ID or of an exception
     */
    @Override
    public void updateOrder(String orderId, LimitOrder order, Consumer<Future<String>> consumer) {
        cancelOrder(orderId, success -> {
            SettableFuture<String> result = SettableFuture.create();
            try {
                if (success.get()) {
                    placeLimitOrder(order, consumer);
                    return;
                } else {
                    result.setException(new IllegalStateException("Order filled"));
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                result.setException(e);
            }
            consumer.accept(result);
        });
    }
}
