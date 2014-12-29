package com.hashnot.fx.framework.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.hashnot.fx.framework.IOrderBookSideListener;
import com.hashnot.fx.framework.IOrderBookSideMonitor;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.framework.OrderBookSideUpdateEvent;
import com.hashnot.fx.util.OrderBooks;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.market.IOrderBookListener;
import com.hashnot.xchange.event.market.OrderBookUpdateEvent;
import com.hashnot.xchange.ext.Market;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hashnot.fx.util.Multimaps.emptyMultimap;
import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;
import static com.xeiam.xchange.dto.Order.OrderType;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;

/**
 * @author Rafał Krupiński
 */
public class OrderBookSideMonitor implements IOrderBookSideMonitor, IOrderBookListener {
    final private static Logger log = LoggerFactory.getLogger(OrderBookSideMonitor.class);
    final protected Map<Exchange, IExchangeMonitor> monitors;

    final private Map<Market, OrderBook> orderBooks = new ConcurrentHashMap<>();

    final protected Map<Market, Multimap<OrderType, IOrderBookSideListener>> listeners = new LinkedHashMap<>();

    public OrderBookSideMonitor(Map<Exchange, IExchangeMonitor> monitors) {
        this.monitors = monitors;
    }

    @Override
    public void addOrderBookSideListener(IOrderBookSideListener listener, MarketSide source) {
        Market market = source.market;
        Multimap<OrderType, IOrderBookSideListener> marketListeners = listeners.computeIfAbsent(market, k -> MultimapBuilder.SetMultimapBuilder.enumKeys(OrderType.class).linkedHashSetValues().build());

        if (marketListeners.put(source.side, listener)) {
            log.info("{}@{} + {}", source, this, listener);
            monitors.get(market.exchange).getOrderBookMonitor().addOrderBookListener(this, market.listing);
        } else {
            log.debug("Duplicate registration {} @ {}@", listener, source, this);
        }
    }

    @Override
    public void removeOrderBookSideListener(IOrderBookSideListener listener, MarketSide source) {
        Market market = source.market;
        Multimap<OrderType, IOrderBookSideListener> marketListeners = listeners.get(market);

        if (marketListeners == null) {
            log.debug("No such listener {} @ {}@", listener, source, this);
            return;
        }

        log.info("{}@{} - {}", source, this, listener);
        marketListeners.remove(source.side, listener);

        if (marketListeners.isEmpty()) {
            monitors.get(market.exchange).getOrderBookMonitor().removeOrderBookListener(this, market.listing);
            orderBooks.remove(market);
        }
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

        if (oldBook != null && oldBook.getTimeStamp() != null && oldBook.getTimeStamp().equals(newBook.getTimeStamp())) {
            log.debug("Skip, same timestamp");
            return;
        }

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
        multiplex(marketListeners.get(side), obse, IOrderBookSideListener::orderBookSideChanged);
    }

    protected boolean hasListener(IOrderBookSideListener listener, MarketSide source) {
        return listeners.getOrDefault(source.market, emptyMultimap()).containsEntry(source.side, listener);
    }

    protected boolean hasListener(Market market) {
        return !listeners.getOrDefault(market, emptyMultimap()).isEmpty();
    }

    @Override
    public String toString() {
        // intended singleton object, class name should suffice
        return getClass().getSimpleName();
    }
}
