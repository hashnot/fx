package com.hashnot.xchange.async.trade;

import com.hashnot.xchange.async.AbstractAsyncService;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.polling.trade.TradeHistoryParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class AsyncTradeService extends AbstractAsyncService implements IAsyncTradeService {
    final private Logger log = LoggerFactory.getLogger(AsyncTradeService.class);

    final private PollingTradeService service;

    public AsyncTradeService(Executor remoteExecutor, PollingTradeService pollingTradeService, Executor consumerExecutor) {
        super(remoteExecutor, consumerExecutor);
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
    public Future<String> placeLimitOrder(LimitOrder limitOrder, Consumer<Future<String>> consumer) {
        return call(() -> service.placeLimitOrder(limitOrder), consumer);
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws InterruptedException, IOException {
        try {
            return call(() -> service.placeLimitOrder(limitOrder), null).get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException)
                throw (IOException) cause;
            else if (cause instanceof ExchangeException)
                throw (ExchangeException) cause;
            else
                throw new ExchangeException("Error", cause);
        }
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

    @Override
    public String toString() {
        return "Async " + service;
    }
}
