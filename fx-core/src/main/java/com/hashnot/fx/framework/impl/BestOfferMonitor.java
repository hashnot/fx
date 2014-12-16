package com.hashnot.fx.framework.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder.SetMultimapBuilder;
import com.google.common.collect.Multimaps;
import com.hashnot.fx.framework.*;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.market.ITickerListener;
import com.hashnot.xchange.event.market.TickerEvent;
import com.hashnot.xchange.ext.Market;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.hashnot.fx.util.Multimaps.emptyMultimap;
import static com.hashnot.xchange.ext.util.Comparables.eq;
import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;
import static com.hashnot.xchange.ext.util.Orders.revert;
import static com.hashnot.xchange.ext.util.Prices.forNull;
import static com.xeiam.xchange.dto.Order.OrderType;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;

/**
 * Best offer and Order book monitor.
 * if an order book of a given market is monitored, then best offer if taken from order book updates. Otherwise ticker is used.
 *
 * @author Rafał Krupiński
 */
public class BestOfferMonitor extends OrderBookSideMonitor implements IBestOfferMonitor, IOrderBookSideMonitor, IOrderBookSideListener, ITickerListener, BestOfferMonitorMBean {
    final private Map<Market, Multimap<OrderType, IBestOfferListener>> bestOfferListeners = new HashMap<>();

    // (exchange, currencyPair, OrderType) -> best offer price
    final private Map<MarketSide, BigDecimal> cache = new ConcurrentHashMap<>();

    public BestOfferMonitor(Map<Exchange, IExchangeMonitor> monitors) {
        super(monitors);
    }

    @Override
    public void ticker(TickerEvent evt) {
        Ticker ticker = evt.ticker;
        Market market = new Market(evt.source, ticker.getCurrencyPair());

        if (hasListener(market)) {
            log.warn("Received a ticker update from {} but listening for order book updates", market);
            return;
        }

        if (!bestOfferListeners.containsKey(market)) {
            log.warn("Received a ticker update from {} but not listening", market);
            return;
        }

        checkBestOffer(ticker.getAsk(), new MarketSide(market, ASK));
        checkBestOffer(ticker.getBid(), new MarketSide(market, BID));
    }

    protected void checkBestOffer(BigDecimal price, MarketSide key) {
        BigDecimal cached = cache.put(key, price);
        if (!eq(price, cached)) {
            log.info("New best offer @{} = {}", key, price);
            notifyBestOfferListeners(new BestOfferEvent(price, key));
        } else
            log.debug("Best offer @{} didn't change", key);
    }

    @Override
    public void orderBookSideChanged(OrderBookSideUpdateEvent evt) {
        // don't call OrderBookListener, they are registered as listeners directly in OBM
        // they are here just so we know what to listen to.

        // we should only be notified only if we have both best offer and order book listeners
        if (!(hasListener(evt.source.market) && !bestOfferListeners.getOrDefault(evt.source.market, emptyMultimap()).isEmpty())) {
            log.warn("Received an OrderBook update bot no listeners are registered");
            return;
        }

        checkBestOffer(evt);
    }

    private void checkBestOffer(OrderBookSideUpdateEvent evt) {
        OrderType side = evt.source.side;
        BigDecimal curPrice = getBestOfferPrice(evt.newOrders, side);

        checkBestOffer(curPrice, evt.source);
    }

    protected BigDecimal getBestOfferPrice(List<LimitOrder> orders, OrderType type) {
        BigDecimal value;
        if (orders.isEmpty())
            value = forNull(type);
        else
            value = orders.get(0).getLimitPrice();
        return value;
    }

    protected void notifyBestOfferListeners(BestOfferEvent bestOfferEvent) {
        MarketSide source = bestOfferEvent.source;
        multiplex(bestOfferListeners.getOrDefault(source.market, emptyMultimap()).get(source.side), bestOfferEvent, IBestOfferListener::updateBestOffer);
    }


    // --- listener registration and deregistraton logic


    @Override
    public void addBestOfferListener(IBestOfferListener listener, MarketSide source) {
        Market market = source.market;
        Multimap<OrderType, IBestOfferListener> marketListeners = bestOfferListeners.computeIfAbsent(market, (k) -> SetMultimapBuilder.enumKeys(OrderType.class).linkedHashSetValues().build());
        if (marketListeners.containsEntry(source.side, listener)) {
            log.debug("Duplicate registration {} @ {}", listener, source);
            return;
        }

        log.info("{} + {}", source, listener);
        marketListeners.put(source.side, listener);

        if (hasListener(market)) {
            registerAsOrderBookListener(market);
        } else {
            registerAsTickerListener(market);
        }
    }

    @Override
    public void removeBestOfferListener(IBestOfferListener listener, MarketSide source) {
        Market market = source.market;
        OrderType side = source.side;
        Multimap<OrderType, IBestOfferListener> marketBestOfferListeners = bestOfferListeners.getOrDefault(market, emptyMultimap());
        if (!marketBestOfferListeners.containsEntry(side, listener)) {
            log.debug("No such listener {} @ {}", listener, source);
            return;
        }

        log.info("{} - {}", source, listener);
        marketBestOfferListeners.remove(side, listener);

        if (marketBestOfferListeners.isEmpty()) {
            if (hasListener(market)) {
                MarketSide sourceRev = new MarketSide(source.market, revert(side));
                super.removeOrderBookSideListener(this, sourceRev);
                super.removeOrderBookSideListener(this, source);
            } else {
                monitors.get(market.exchange).getTickerMonitor().removeTickerListener(this, market.listing);
            }
        }
    }

    @Override
    public void addOrderBookSideListener(IOrderBookSideListener listener, MarketSide source) {
        if (hasListener(listener, source)) {
            log.debug("Duplicate registration {} @ {}", listener, source);
            return;
        }

        log.info("{} + {}", source, listener);
        super.addOrderBookSideListener(listener, source);

        Market market = source.market;
        if (bestOfferListeners.containsKey(market)) {
            registerAsOrderBookListener(market);
        }
    }

    @Override
    public void removeOrderBookSideListener(IOrderBookSideListener listener, MarketSide source) {
        if (!hasListener(listener, source)) {
            log.debug("No such listener {} @ {}", listener, source);
            return;
        }

        log.info("{} - {}", source, listener);
        super.removeOrderBookSideListener(listener, source);

        Market market = source.market;

        // start listening on ticker monitor if there are no other order book listeners for given market

        Multimap<OrderType, IOrderBookSideListener> marketListeners = listeners.getOrDefault(market, emptyMultimap());
        if (marketListeners.size() == 2) {
            if (bestOfferListeners.containsKey(market)) {
                registerAsTickerListener(market);
            }
        }
    }

    protected void registerAsOrderBookListener(Market market) {
        super.addOrderBookSideListener(this, new MarketSide(market, ASK));
        super.addOrderBookSideListener(this, new MarketSide(market, BID));
        monitors.get(market.exchange).getTickerMonitor().removeTickerListener(this, market.listing);
    }

    protected void registerAsTickerListener(Market market) {
        super.removeOrderBookSideListener(this, new MarketSide(market, ASK));
        super.removeOrderBookSideListener(this, new MarketSide(market, BID));
        monitors.get(market.exchange).getTickerMonitor().addTickerListener(this, market.listing);
    }

    @Override
    public Map<String, String> getListeners() {
        Map<String, String> result = new HashMap<>();
        bestOfferListeners.forEach((market, mm) -> Multimaps.asMap(mm).forEach((type, v) -> result.put(type + "@" + market, String.join(" ", v.toString()))));
        return result;
    }

    @Override
    public Map<String, BigDecimal> getBestPrices() {
        return cache.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
    }
}
