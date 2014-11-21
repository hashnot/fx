package com.hashnot.xchange.async.trade;

import com.google.common.util.concurrent.SettableFuture;
import com.hashnot.xchange.async.RunnableScheduler;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.polling.trade.TradeHistoryParams;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class AsyncTradeService implements IAsyncTradeService {
    final private RunnableScheduler scheduler;
    final private PollingTradeService service;

    public AsyncTradeService(RunnableScheduler scheduler, PollingTradeService pollingTradeService) {
        this.scheduler = scheduler;
        service = pollingTradeService;
    }

    @Override
    public void getOpenOrders(Consumer<Future<OpenOrders>> consumer) {
        scheduler.priority(() -> {
            SettableFuture<OpenOrders> result = SettableFuture.create();
            try {
                result.set(service.getOpenOrders());
            } catch (IOException e) {
                result.setException(e);
            }
            consumer.accept(result);
        });
    }

    @Override
    public void placeMarketOrder(MarketOrder marketOrder, Consumer<Future<String>> consumer) {
        scheduler.priority(() -> {
            SettableFuture<String> result = SettableFuture.create();
            try {
                result.set(service.placeMarketOrder(marketOrder));
            } catch (IOException e) {
                result.setException(e);
            }
            consumer.accept(result);
        });
    }

    @Override
    public void placeLimitOrder(LimitOrder limitOrder, Consumer<Future<String>> consumer) {
        scheduler.priority(() -> {
            SettableFuture<String> result = SettableFuture.create();
            try {
                result.set(service.placeLimitOrder(limitOrder));
            } catch (IOException e) {
                result.setException(e);
            }
            consumer.accept(result);
        });
    }

    @Override
    public void cancelOrder(String orderId, Consumer<Future<Boolean>> consumer) {
        scheduler.priority(() -> {
            SettableFuture<Boolean> result = SettableFuture.create();
            try {
                result.set(service.cancelOrder(orderId));
            } catch (IOException e) {
                result.setException(e);
            }
            consumer.accept(result);
        });
    }

    @Override
    public void getTradeHistory(Consumer<Future<UserTrades>> consumer, Object... arguments) {
        scheduler.priority(() -> {
            SettableFuture<UserTrades> result = SettableFuture.create();
            try {
                result.set(service.getTradeHistory(arguments));
            } catch (IOException e) {
                result.setException(e);
            }
            consumer.accept(result);
        });
    }

    @Override
    public void getTradeHistory(TradeHistoryParams params, Consumer<Future<UserTrades>> consumer) {
        scheduler.priority(() -> {
            SettableFuture<UserTrades> result = SettableFuture.create();
            try {
                result.set(service.getTradeHistory(params));
            } catch (IOException e) {
                result.setException(e);
            }
            consumer.accept(result);
        });
    }

    @Override
    public TradeHistoryParams createTradeHistoryParams() {
        return service.createTradeHistoryParams();
    }

    @Override
    public void getExchangeSymbols(Consumer<Future<Collection<CurrencyPair>>> consumer) {
        scheduler.priority(() -> {
            SettableFuture<Collection<CurrencyPair>> result = SettableFuture.create();
            try {
                result.set(service.getExchangeSymbols());
            } catch (IOException e) {
                result.setException(e);
            }
            consumer.accept(result);
        });
    }

    /**
     * Update existing LimitOrder with a new one
     * <p>
     * TODO support exchanges with update operation
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
