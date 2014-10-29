package com.hashnot.fx.ext.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.fx.OrderBookUpdateEvent;
import com.hashnot.fx.ext.*;
import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.currency.CurrencyPair;
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
 * <p/>
 * if an order book of a given market is monitored, then best offer if taken from order book updates. Otherwise ticker is used.
 *
 * @author Rafał Krupiński
 */
public class BestOfferMonitor implements IBestOfferMonitor, IOrderBookListener, ITickerListener, IOrderBookMonitor {
    final private static Logger log = LoggerFactory.getLogger(BestOfferMonitor.class);

    final private Multimap<Market, IBestOfferListener> bestOfferListeners = Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);

    final private Multimap<Market, IOrderBookListener> orderBookListeners = Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);

    // (exchange, currencyPair, OrderType) -> best offer price
    final private Map<MarketSide, BigDecimal> cache = new HashMap<>();

    @Override
    public void ticker(Ticker ticker, IExchange source) {
        Market market = new Market(source, ticker.getCurrencyPair());

        if (!bestOfferListeners.containsKey(market)) {
            log.warn("Received a ticker update but no listeners are registered");
            return;
        }

        if (orderBookListeners.containsKey(market)) {
            log.warn("Received a ticker update but listening for order book updates");
            return;
        }

        checkBestOffer(market, ticker.getAsk(), ASK);
        checkBestOffer(market, ticker.getBid(), BID);
    }

    protected void checkBestOffer(Market market, BigDecimal price, Order.OrderType side) {
        MarketSide key = new MarketSide(market.exchange, market.listing, side);
        BigDecimal cached = cache.getOrDefault(key, forNull(side));

        if (eq(price, cached))
            return;

        BestOfferEvent evt = new BestOfferEvent(price, side, market);
        cache.put(key, price);
        notifyBestOfferListeners(market, evt);
    }

    @Override
    public void orderBookChanged(OrderBookUpdateEvent evt) {
        // don't call OrderBookListener, they are registered as listeners directly in OBM
        // they are here just so we know what to listen to.

        // we should only be notified if we have both best offer and order book listeners
        if (!orderBookListeners.containsKey(evt.source) || !bestOfferListeners.containsKey(evt.source)) {
            log.warn("Received an OrderBook update bot no listeners are registered");
            return;
        }

        checkBestOffer(evt, ASK);
        checkBestOffer(evt, BID);
    }

    private void checkBestOffer(OrderBookUpdateEvent evt, Order.OrderType side) {
        BigDecimal curPrice = getBestOfferPrice(evt, side);

        MarketSide key = new MarketSide(evt.source.exchange, evt.source.listing, side);
        BigDecimal cached = cache.getOrDefault(key, forNull(side));
        if (eq(curPrice, cached))
            return;

        cache.put(key, curPrice);
        notifyBestOfferListeners(evt.source, new BestOfferEvent(curPrice, side, evt.source));
    }

    protected BigDecimal getBestOfferPrice(OrderBookUpdateEvent evt, Order.OrderType type) {
        List<LimitOrder> orders = evt.after.getOrders(type);
        BigDecimal value;
        if (orders.isEmpty())
            value = forNull(type);
        else
            value = orders.get(0).getLimitPrice();
        return value;
    }

    protected void notifyBestOfferListeners(Market market, BestOfferEvent bestOfferEvent) {
        for (IBestOfferListener listener : bestOfferListeners.get(market))
            listener.updateBestOffer(bestOfferEvent);
    }


    @Override
    public void addBestOfferListener(IBestOfferListener listener, Market market) {
        bestOfferListeners.put(market, listener);

        if (orderBookListeners.containsKey(market))
            market.exchange.getOrderBookMonitor().addOrderBookListener(this, market);
        else
            market.exchange.getTickerMonitor().addTickerListener(this, market);
    }

    @Override
    public void removeBestOfferListener(IBestOfferListener listener, Market market) {
        bestOfferListeners.remove(market, listener);

        if (!bestOfferListeners.containsKey(market)) {
            market.exchange.getTickerMonitor().removeTickerListener(this, market);

            if (!orderBookListeners.containsKey(market))
                market.exchange.getOrderBookMonitor().removeOrderBookListener(this, market);
        }
    }

    @Override
    public void addOrderBookListener(IOrderBookListener listener, Market market) {
        orderBookListeners.put(market, listener);
        market.exchange.getOrderBookMonitor().addOrderBookListener(listener, market);

        if (bestOfferListeners.containsKey(market)) {
            market.exchange.getTickerMonitor().removeTickerListener(this, market);
            market.exchange.getOrderBookMonitor().addOrderBookListener(this, market);
        }
    }

    @Override
    public void removeOrderBookListener(IOrderBookListener listener, Market market) {
        orderBookListeners.remove(market, listener);
        market.exchange.getOrderBookMonitor().removeOrderBookListener(listener, market);

        if (bestOfferListeners.containsKey(market) && !orderBookListeners.containsKey(market)) {
            market.exchange.getTickerMonitor().addTickerListener(this, market);
            market.exchange.getOrderBookMonitor().removeOrderBookListener(this, market);
        }
    }

    private static class MarketSide extends Market {

        public final Order.OrderType side;

        public MarketSide(IExchange exchange, CurrencyPair listing, Order.OrderType side) {
            super(exchange, listing);
            this.side = side;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o != null && o instanceof MarketSide && equals((MarketSide) o);
        }

        protected boolean equals(MarketSide that) {
            return super.equals(that) && side == that.side;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + side.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return side.name() + '/' + super.toString();
        }
    }
}
