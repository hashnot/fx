package com.hashnot.fx.ext.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.fx.ext.*;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.hashnot.fx.util.Numbers.eq;
import static com.hashnot.fx.util.Orders.Price.forNull;
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

    final private Multimap<MarketSide, IBestOfferListener> bestOfferListeners = Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);

    final private Multimap<MarketSide, IOrderBookSideListener> orderBookListeners = Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);

    // (exchange, currencyPair, OrderType) -> best offer price
    final private Map<MarketSide, BigDecimal> cache = new HashMap<>();

    @Override
    public void ticker(Ticker ticker, IExchange source) {
        Market market = new Market(source, ticker.getCurrencyPair());

        boolean serviced = checkBestOffer(market, ticker.getAsk(), ASK);
        serviced |= checkBestOffer(market, ticker.getBid(), BID);

        if (!serviced)
            log.warn("Received a ticker update from {} but no listeners are registered", market);
    }

    protected boolean checkBestOffer(Market market, BigDecimal price, Order.OrderType side) {
        MarketSide key = new MarketSide(market, side);

        if (!bestOfferListeners.containsKey(key)) {
            return false;
        }

        if (orderBookListeners.containsKey(key)) {
            log.warn("Received a ticker update from {} but listening for order book updates", market);
            return false;
        }

        BigDecimal cached = cache.getOrDefault(key, forNull(side));

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
        if (!orderBookListeners.containsKey(evt.source) || !bestOfferListeners.containsKey(evt.source)) {
            log.warn("Received an OrderBook update bot no listeners are registered");
            return;
        }

        checkBestOffer(evt);
    }

    private void checkBestOffer(OrderBookSideUpdateEvent evt) {
        Order.OrderType side = evt.source.side;
        BigDecimal curPrice = getBestOfferPrice(evt.newOrders, side);

        BigDecimal cached = cache.getOrDefault(evt.source, forNull(side));
        if (eq(curPrice, cached))
            return;

        cache.put(evt.source, curPrice);
        notifyBestOfferListeners(new BestOfferEvent(curPrice, evt.source));
    }

    protected BigDecimal getBestOfferPrice(List<LimitOrder> orders, Order.OrderType type) {
        BigDecimal value;
        if (orders.isEmpty())
            value = forNull(type);
        else
            value = orders.get(0).getLimitPrice();
        return value;
    }

    protected void notifyBestOfferListeners(BestOfferEvent bestOfferEvent) {
        for (IBestOfferListener listener : bestOfferListeners.get(bestOfferEvent.source)) {
            try {
                listener.updateBestOffer(bestOfferEvent);
            } catch (RuntimeException e) {
                log.warn("Error", e);
            }
        }
    }


    @Override
    public void addBestOfferListener(IBestOfferListener listener, MarketSide source) {
        boolean listen = bestOfferListeners.put(source, listener);
        if (!listen)
            return;

        if (!orderBookListeners.containsKey(source)) {
            registerTickerListener(source);
        } else {
            super.addOrderBookSideListener(this, source);
        }
    }

    @Override
    public void removeBestOfferListener(IBestOfferListener listener, MarketSide source) {
        bestOfferListeners.remove(source, listener);

        if (!bestOfferListeners.containsKey(source)) {
            deregisterTickerListener(source);
            super.removeOrderBookSideListener(this, source);
        }
    }

    @Override
    public void addOrderBookSideListener(IOrderBookSideListener listener, MarketSide source) {
        boolean listen = orderBookListeners.put(source, listener);
        if (!listen)
            return;

        super.addOrderBookSideListener(listener, source);

        if (bestOfferListeners.containsKey(source)) {
            deregisterTickerListener(source);
            super.addOrderBookSideListener(this, source);
        }
    }

    @Override
    public void removeOrderBookSideListener(IOrderBookSideListener listener, MarketSide source) {
        boolean remove = orderBookListeners.remove(source, listener);
        if (!remove)
            return;

        super.removeOrderBookSideListener(listener, source);

        if (bestOfferListeners.containsKey(source) && !orderBookListeners.containsKey(source)) {
            registerTickerListener(source);
            super.removeOrderBookSideListener(this, source);
        }
    }

    protected void deregisterTickerListener(MarketSide source) {
        Market market = source.market;
        // don't deregister if we already are registered
        if (!bestOfferListeners.containsKey(new MarketSide(market, Orders.revert(source.side)))) {
            market.exchange.getTickerMonitor().removeTickerListener(this, market);
        }
    }

    protected void registerTickerListener(MarketSide source) {
        Market market = source.market;
        // don't register if we already are registered
        if (!bestOfferListeners.containsKey(new MarketSide(market, Orders.revert(source.side)))) {
            market.exchange.getTickerMonitor().addTickerListener(this, market);
        }
    }

}
