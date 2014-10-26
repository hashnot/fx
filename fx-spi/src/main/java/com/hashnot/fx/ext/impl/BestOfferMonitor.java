package com.hashnot.fx.ext.impl;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.fx.OrderBookUpdateEvent;
import com.hashnot.fx.ext.*;
import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Best offer and Order book monitor.
 * <p/>
 * if an order book of a given market is monitored, then best offer if taken from order book updates. Otherwise ticker is used.
 *
 * @author Rafał Krupiński
 */
public class BestOfferMonitor implements IBestOfferMonitor, IOrderBookListener, ITickerListener, IOrderBookMonitor {
    final private static Logger log = LoggerFactory.getLogger(BestOfferMonitor.class);

    final private IOrderBookMonitor orderBookMonitor;

    final private ITickerMonitor tickerMonitor;

    final private Multimap<Market, IBestOfferListener> bestOfferListeners = Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);

    final private Multimap<Market, IOrderBookListener> orderBookListeners = Multimaps.newSetMultimap(new HashMap<>(), HashSet::new);

    @Override
    public void orderBookChanged(OrderBookUpdateEvent evt) {
        // don't call OrderBookListener, they are registered as listeners directly in OBM
        // they are here just so we know what to listen to.

        Market market = new Market(evt.source, evt.pair);

        // we should only be notified if we have both best offer and order book listeners
        if (!orderBookListeners.containsKey(market) || !bestOfferListeners.containsKey(market)) {
            log.warn("Received an OrderBook update bot no listeners are registered");
            return;
        }


        for (Order.OrderType type : Order.OrderType.values()) {
            List<LimitOrder> orders = evt.after.getOrders(type);
            BigDecimal value;
            if (orders.isEmpty())
                value = null;
            else
                value = orders.get(0).getLimitPrice();

            notifyBestOfferListeners(market, new BestOfferEvent(value, type, market));
        }
    }

    @Override
    public void ticker(Ticker ticker, IExchange source) {
        Market market = new Market(source, ticker.getCurrencyPair());

        if(!bestOfferListeners.containsKey(market)){
            log.warn("Received a ticker update but no listeners are registered");
            return;
        }

        if(orderBookListeners.containsKey(market)){
            log.warn("Received a ticker update but listening for order book updates");
            return;
        }

        BestOfferEvent ask = new BestOfferEvent(ticker.getAsk(), Order.OrderType.ASK, market);
        notifyBestOfferListeners(market, ask);

        BestOfferEvent bid = new BestOfferEvent(ticker.getBid(), Order.OrderType.BID, market);
        notifyBestOfferListeners(market, bid);
    }

    protected void notifyBestOfferListeners(Market market, BestOfferEvent bestOfferEvent) {
        for (IBestOfferListener listener : bestOfferListeners.get(market))
            listener.updateBestOffer(bestOfferEvent);
    }


    @Override
    public void addBestOfferListener(IBestOfferListener listener, Market market) {
        bestOfferListeners.put(market, listener);

        if (orderBookListeners.containsKey(market))
            orderBookMonitor.addOrderBookListener(this, market);
        else
            tickerMonitor.addTickerListener(this, market);
    }

    @Override
    public void removeBestOfferListener(IBestOfferListener listener, Market market) {
        bestOfferListeners.remove(market, listener);

        if (!bestOfferListeners.containsKey(market)) {
            tickerMonitor.removeTickerListener(this, market);

            if (!orderBookListeners.containsKey(market))
                orderBookMonitor.removeOrderBookListener(this, market);
        }
    }

    @Override
    public void addOrderBookListener(IOrderBookListener listener, Market market) {
        orderBookListeners.put(market, listener);
        orderBookMonitor.addOrderBookListener(listener, market);

        if (bestOfferListeners.containsKey(market)) {
            tickerMonitor.removeTickerListener(this, market);
            orderBookMonitor.addOrderBookListener(this, market);
        }
    }

    @Override
    public void removeOrderBookListener(IOrderBookListener listener, Market market) {
        orderBookListeners.remove(market, listener);
        orderBookMonitor.removeOrderBookListener(listener, market);

        if (bestOfferListeners.containsKey(market) && !orderBookListeners.containsKey(market)) {
            tickerMonitor.addTickerListener(this, market);
            orderBookMonitor.removeOrderBookListener(this, market);
        }
    }

    public BestOfferMonitor(IOrderBookMonitor orderBookMonitor, ITickerMonitor tickerMonitor) {
        this.orderBookMonitor = orderBookMonitor;
        this.tickerMonitor = tickerMonitor;
    }

}
