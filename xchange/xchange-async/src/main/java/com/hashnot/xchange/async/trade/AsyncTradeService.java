package com.hashnot.xchange.async.trade;

import com.hashnot.xchange.async.impl.ListenerCollection;
import com.hashnot.xchange.ext.trade.IOrderPlacementListener;
import com.hashnot.xchange.ext.trade.OrderCancelEvent;
import com.hashnot.xchange.ext.trade.OrderPlacementEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.TradeServiceHelper;
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
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static com.hashnot.xchange.async.impl.AsyncSupport.call;
import static com.hashnot.xchange.async.impl.AsyncSupport.get;

/**
 * @author Rafał Krupiński
 */
public class AsyncTradeService implements IAsyncTradeService {
    final protected Logger log = LoggerFactory.getLogger(getClass());

    final private Executor executor;
    final private Exchange exchange;
    final private PollingTradeService service;
    final private ListenerCollection<IOrderPlacementListener> listeners;

    public AsyncTradeService(Executor remoteExecutor, Exchange exchange) {
        executor = remoteExecutor;
        this.exchange = exchange;
        service = exchange.getPollingTradeService();
        listeners = new ListenerCollection<>(this);
    }

    @Override
    public void getOpenOrders(Consumer<Future<OpenOrders>> consumer) {
        call(service::getOpenOrders, consumer, executor);
    }

    @Override
    public void placeMarketOrder(MarketOrder marketOrder, Consumer<Future<String>> consumer) {
        call(() -> doPlaceMarketOrder(marketOrder), consumer, executor);
    }

    protected String doPlaceMarketOrder(MarketOrder order) throws IOException {
        log.debug("Place @{} {}", this, order);
        String result = service.placeMarketOrder(order);
        log.debug("Order {}", result);
        OrderPlacementEvent<MarketOrder> evt = new OrderPlacementEvent<>(result, order, exchange);
        listeners.fire(evt, IOrderPlacementListener::marketOrderPlaced);
        return result;
    }

    @Override
    public Future<String> placeLimitOrder(LimitOrder limitOrder, Consumer<Future<String>> consumer) {
        return call(() -> doPlaceLimitOrder(limitOrder), consumer, executor);
    }

    protected String doPlaceLimitOrder(LimitOrder order) throws IOException {
        log.debug("Place @{} {}", this, order);
        String result = service.placeLimitOrder(order);
        log.debug("Order {}", result);
        listeners.fire(new OrderPlacementEvent<>(result, order, exchange), IOrderPlacementListener::limitOrderPlaced);
        return result;
    }

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws InterruptedException, IOException {
        return get(placeLimitOrder(limitOrder, null));
    }

    @Override
    public Future<Boolean> cancelOrder(String orderId, Consumer<Future<Boolean>> consumer) {
        return call(() -> doCancelOrder(orderId), consumer, executor);
    }

    protected boolean doCancelOrder(String orderId) throws IOException {
        log.debug("Cancel {}@{}", orderId, this);
        boolean result = service.cancelOrder(orderId);
        listeners.fire(new OrderCancelEvent(orderId, exchange, result), IOrderPlacementListener::orderCanceled);
        if (result)
            log.debug("Order {} canceled @{}", orderId, this);
        else
            log.warn("Unsuccessful cancel order {}@{}", orderId, this);

        return result;
    }

    @Override
    public void getTradeHistory(Consumer<Future<UserTrades>> consumer, Object... arguments) {
        call(() -> service.getTradeHistory(arguments), consumer, executor);
    }

    @Override
    public void getTradeHistory(TradeHistoryParams params, Consumer<Future<UserTrades>> consumer) {
        call(() -> service.getTradeHistory(params), consumer, executor);
    }

    @Override
    public TradeHistoryParams createTradeHistoryParams() {
        return service.createTradeHistoryParams();
    }

    @Override
    public void getExchangeSymbols(Consumer<Future<Collection<CurrencyPair>>> consumer) {
        call(service::getExchangeSymbols, consumer, executor);
    }

    @Override
    public Future<Map<CurrencyPair, ? extends TradeServiceHelper>> getMetadata(Consumer<Future<Map<CurrencyPair, ? extends TradeServiceHelper>>> consumer) {
        return call(service::getTradeServiceHelperMap, consumer, executor);
    }

    @Override
    public void addLimitOrderPlacedListener(IOrderPlacementListener listener) {
        listeners.addListener(listener);
    }

    @Override
    public void removeLimitOrderPlacedListener(IOrderPlacementListener listener) {
        listeners.removeListener(listener);
    }

    @Override
    public String toString() {
        return "AsyncTradeService" + "@" + exchange;
    }

}
