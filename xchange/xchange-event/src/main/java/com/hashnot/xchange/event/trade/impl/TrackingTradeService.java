package com.hashnot.xchange.event.trade.impl;

import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.impl.AsyncSupport;
import com.hashnot.xchange.async.impl.ListenerMap;
import com.hashnot.xchange.event.trade.IOrderTradesListener;
import com.hashnot.xchange.event.trade.ITrackingTradeService;
import com.hashnot.xchange.event.trade.IUserTradeListener;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.hashnot.xchange.ext.trade.OrderCancelEvent;
import com.hashnot.xchange.ext.trade.OrderPlacementEvent;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Future;

public class TrackingTradeService implements ITrackingTradeService, IUserTradeListener {
    final private static Logger log = LoggerFactory.getLogger(TrackingTradeService.class);

    final protected IAsyncExchange exchange;

    final protected ListenerMap<String, IOrderTradesListener> tradeListeners;

    public TrackingTradeService(IAsyncExchange exchange) {
        this.exchange = exchange;
        tradeListeners = new ListenerMap<>(this.exchange);
    }

    @Override
    public void placeLimitOrder(LimitOrder order, IOrderTradesListener listener) {
        exchange.getTradeService().placeLimitOrder(order, f -> handleLimitOrderPlaced(f, order, listener));
    }

    protected void handleLimitOrderPlaced(Future<String> future, LimitOrder order, IOrderTradesListener listener) {
        try {
            String orderId = AsyncSupport.get(future);
            tradeListeners.addListener(orderId, listener);
            listener.limitOrderPlaced(new OrderPlacementEvent<>(orderId, order, exchange.getExchange()));
        } catch (RuntimeException | IOException e) {
            log.warn("Error from {}", this, e);
            listener.orderPlacementFailed(order);
        }
    }

    @Override
    public void placeMarketOrder(MarketOrder order, IOrderTradesListener listener) {
        exchange.getTradeService().placeMarketOrder(order, f -> handleMarketOrderPlaced(f, order, listener));
    }

    protected void handleMarketOrderPlaced(Future<String> future, MarketOrder order, IOrderTradesListener listener) {
        try {
            String orderId = AsyncSupport.get(future);
            tradeListeners.addListener(orderId, listener);
            listener.marketOrderPlaced(new OrderPlacementEvent<>(orderId, order, exchange.getExchange()));
        } catch (RuntimeException | IOException e) {
            log.warn("Error from {}", this, e);
        }
    }

    @Override
    public void cancelOrder(String orderId) {
        exchange.getTradeService().cancelOrder(orderId, f -> handleOrderCancelled(f, orderId));
    }

    protected void handleOrderCancelled(Future<Boolean> f, String orderId) {
        try {
            Boolean result = AsyncSupport.get(f);
            tradeListeners.fire(orderId, new OrderCancelEvent(orderId, exchange.getExchange(), result), IOrderTradesListener::orderCanceled);
            tradeListeners.removeListeners(orderId);
        } catch (RuntimeException | IOException e) {
            log.warn("Error from {}", this, e);
        }
    }

    @Override
    public void trade(UserTradeEvent evt) {
        String orderId = evt.monitored.getId();
        tradeListeners.fire(orderId, evt, IUserTradeListener::trade);

        if (evt.current == null)
            tradeListeners.removeListeners(orderId);
    }

    @Override
    public String toString() {
        return "TrackingTradeService@" + exchange;
    }
}
