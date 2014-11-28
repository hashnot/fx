package com.hashnot.fx.framework.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.hashnot.fx.framework.*;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.market.ITickerListener;
import com.hashnot.xchange.event.market.TickerEvent;
import com.hashnot.xchange.ext.Market;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;
import static com.hashnot.xchange.ext.util.Numbers.Price.forNull;
import static com.hashnot.xchange.ext.util.Numbers.eq;
import static com.hashnot.xchange.ext.util.Orders.revert;
import static com.xeiam.xchange.dto.Order.OrderType;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;

/**
 * Best offer and Order book monitor.
 * if an order book of a given market is monitored, then best offer if taken from order book updates. Otherwise ticker is used.
 *
 * @author Rafał Krupiński
 */
public class BestOfferMonitor extends OrderBookSideMonitor implements IBestOfferMonitor, IOrderBookSideMonitor, IOrderBookSideListener, ITickerListener {
    final private static Logger log = LoggerFactory.getLogger(BestOfferMonitor.class);

    final private Map<Exchange, IExchangeMonitor> monitors;

    final private Map<Market, Multimap<OrderType, IBestOfferListener>> bestOfferListeners = new HashMap<>();

    final private Multimap<MarketSide, IOrderBookSideListener> orderBookListeners = Multimaps.newSetMultimap(new HashMap<>(), LinkedHashSet::new);

    // (exchange, currencyPair, OrderType) -> best offer price
    final private Map<MarketSide, BigDecimal> cache = new HashMap<>();

    public BestOfferMonitor(Map<Exchange, IExchangeMonitor> monitors) {
        super(monitors);
        this.monitors = monitors;
    }

    @Override
    public void ticker(TickerEvent evt) {
        Ticker ticker = evt.ticker;
        Market market = new Market(evt.source, ticker.getCurrencyPair());

        boolean serviced = checkBestOffer(ticker.getAsk(), new MarketSide(market, ASK));
        serviced |= checkBestOffer(ticker.getBid(), new MarketSide(market, BID));

        if (!serviced)
            log.warn("Received a ticker update from {} but no listeners are registered", market);
    }

    protected boolean checkBestOffer(BigDecimal price, MarketSide key) {
        Market market = key.market;

        if (!bestOfferListeners.containsKey(market)) {
            return false;
        }

        if (orderBookListeners.containsKey(key)) {
            log.warn("Received a ticker update from {} but listening for order book updates", market);
            return false;
        }

        BigDecimal cached = cache.getOrDefault(key, forNull(key.side));

        if (!eq(price, cached)) {
            BestOfferEvent evt = new BestOfferEvent(price, key);
            cache.put(key, price);
            notifyBestOfferListeners(evt);
        }
        return true;
    }

    @Override
    public void orderBookSideChanged(OrderBookSideUpdateEvent evt) {
        // don't call OrderBookListener, they are registered as listeners directly in OBM
        // they are here just so we know what to listen to.

        // we should only be notified if we have both best offer and order book listeners
        if (!(orderBookListeners.containsKey(evt.source) && bestOfferListeners.containsKey(evt.source.market))) {
            log.warn("Received an OrderBook update bot no listeners are registered");
            return;
        }

        checkBestOffer(evt);
    }

    private void checkBestOffer(OrderBookSideUpdateEvent evt) {
        OrderType side = evt.source.side;
        BigDecimal curPrice = getBestOfferPrice(evt.newOrders, side);

        BigDecimal cached = cache.getOrDefault(evt.source, forNull(side));
        if (eq(curPrice, cached))
            return;

        cache.put(evt.source, curPrice);
        notifyBestOfferListeners(new BestOfferEvent(curPrice, evt.source));
    }

    protected BigDecimal getBestOfferPrice(List<LimitOrder> orders, OrderType type) {
        BigDecimal value;
        if (orders.isEmpty())
            value = forNull(type);
        else
            value = orders.get(0).getLimitPrice();
        return value;
    }

    private static final SetMultimap<OrderType, IBestOfferListener> EMPTY_MULTIMAP = Multimaps.newSetMultimap(new EnumMap<>(OrderType.class), LinkedHashSet::new);

    protected void notifyBestOfferListeners(BestOfferEvent bestOfferEvent) {
        MarketSide source = bestOfferEvent.source;
        multiplex(bestOfferListeners.getOrDefault(source.market, EMPTY_MULTIMAP).get(source.side), bestOfferEvent, IBestOfferListener::updateBestOffer);
    }

    @Override
    public void addBestOfferListener(IBestOfferListener listener, MarketSide source) {
        boolean listen = bestOfferListeners.computeIfAbsent(source.market, (k) -> Multimaps.newSetMultimap(new EnumMap<>(OrderType.class), LinkedHashSet::new)).put(source.side, listener);
        if (!listen)
            return;

        if (!orderBookListeners.containsKey(source)) {
            monitors.get(source.market.exchange).getTickerMonitor().addTickerListener(this, source.market.listing);
        } else {
            super.addOrderBookSideListener(this, source);
        }
    }

    @Override
    public void removeBestOfferListener(IBestOfferListener listener, MarketSide source) {
        Market market = source.market;
        Multimap<OrderType, IBestOfferListener> marketBestOfferListeners = bestOfferListeners.getOrDefault(market, EMPTY_MULTIMAP);
        marketBestOfferListeners.remove(source.side, listener);

        if (marketBestOfferListeners.isEmpty()) {
            monitors.get(market.exchange).getTickerMonitor().removeTickerListener(this, market.listing);
            super.removeOrderBookSideListener(this, source);
        }
    }

    @Override
    public void addOrderBookSideListener(IOrderBookSideListener listener, MarketSide source) {
        boolean listen = orderBookListeners.put(source, listener);
        if (!listen)
            return;

        super.addOrderBookSideListener(listener, source);

        Market market = source.market;
        if (bestOfferListeners.containsKey(market)) {
            monitors.get(market.exchange).getTickerMonitor().removeTickerListener(this, market.listing);
            super.addOrderBookSideListener(this, source);
        }
    }

    @Override
    public void removeOrderBookSideListener(IOrderBookSideListener listener, MarketSide source) {
        boolean remove = orderBookListeners.remove(source, listener);
        if (!remove)
            return;

        super.removeOrderBookSideListener(listener, source);

        Market market = source.market;
        if (bestOfferListeners.containsKey(market) && !(orderBookListeners.containsKey(source) || orderBookListeners.containsKey(new MarketSide(market, revert(source.side))))) {
            monitors.get(market.exchange).getTickerMonitor().addTickerListener(this, market.listing);
            super.removeOrderBookSideListener(this, source);
        }
    }
}
