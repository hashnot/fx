package com.hashnot.fx.framework.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.fx.framework.IOrderBookSideListener;
import com.hashnot.fx.framework.IOrderBookSideMonitor;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.framework.OrderBookSideUpdateEvent;
import com.hashnot.fx.util.OrderBooks;
import com.hashnot.xchange.event.IOrderBookListener;
import com.hashnot.xchange.event.OrderBookUpdateEvent;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.Market;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.xeiam.xchange.dto.Order.OrderType;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;

/**
 * @author Rafał Krupiński
 */
public class OrderBookSideMonitor implements IOrderBookSideMonitor, IOrderBookListener {
    final private static Logger log = LoggerFactory.getLogger(OrderBookSideMonitor.class);
    final private Map<Exchange, IExchangeMonitor> monitors;

    final private Map<Market, OrderBook> orderBooks = new HashMap<>();

    final private Map<Market, Multimap<OrderType, IOrderBookSideListener>> listeners = new HashMap<>();

    public OrderBookSideMonitor(Map<Exchange, IExchangeMonitor> monitors) {
        this.monitors = monitors;
    }

    @Override
    public void addOrderBookSideListener(IOrderBookSideListener listener, MarketSide source) {
        Market market = source.market;
        Multimap<OrderType, IOrderBookSideListener> marketListeners = listeners.computeIfAbsent(market, k -> Multimaps.newSetMultimap(new HashMap<>(), HashSet::new));

        // avoid race condition, we could get an order book between registration as listener and putting our listener in map
        boolean listen = marketListeners.isEmpty();

        marketListeners.put(source.side, listener);

        if (listen)
            monitors.get(market.exchange).getOrderBookMonitor().addOrderBookListener(this, market.listing);
    }

    @Override
    public void removeOrderBookSideListener(IOrderBookSideListener listener, MarketSide source) {
        Market market = source.market;
        Multimap<OrderType, IOrderBookSideListener> marketListeners = listeners.get(market);

        if (marketListeners == null) {
            log.warn("Removing unregistered listener");
            return;
        }

        marketListeners.remove(source.side, listener);

        if (marketListeners.isEmpty())
            monitors.get(market.exchange).getOrderBookMonitor().removeOrderBookListener(this, market.listing);
    }

    @Override
    public void orderBookChanged(OrderBookUpdateEvent evt) {
        Multimap<OrderType, IOrderBookSideListener> marketListeners = listeners.get(evt.source);
        if (marketListeners == null) {
            log.warn("Got unexpected OrderBook from {}", evt.source);
            return;
        }

        Market market = evt.source;
        OrderBook newBook = evt.orderBook;
        OrderBook oldBook = orderBooks.put(market, newBook);

        checkOrderBookUpdate(oldBook, newBook, marketListeners, market, ASK);
        checkOrderBookUpdate(oldBook, newBook, marketListeners, market, BID);
    }

    private void checkOrderBookUpdate(OrderBook oldBook, OrderBook newBook, Multimap<OrderType, IOrderBookSideListener> marketListeners, Market market, OrderType side) {
        if (!marketListeners.containsKey(side))
            return;

        List<LimitOrder> newOrders = newBook.getOrders(side);
        List<LimitOrder> oldOrders = null;

        if (oldBook != null) {
            oldOrders = oldBook.getOrders(side);

            if (OrderBooks.equals(oldOrders, newOrders)) {
                return;
            }
        }

        OrderBookSideUpdateEvent obse = new OrderBookSideUpdateEvent(new MarketSide(market, side), oldOrders, newOrders);
        for (IOrderBookSideListener listener : marketListeners.get(side)) {
            try {
                listener.orderBookSideChanged(obse);
            } catch (RuntimeException x) {
                log.warn("Error from {}", listener, x);
            }
        }
    }
}
