package com.hashnot.fx.dealer;

import com.hashnot.fx.framework.*;
import com.hashnot.fx.util.OrderBooks;
import com.hashnot.xchange.ext.IExchange;
import com.hashnot.xchange.ext.Market;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

import static com.hashnot.xchange.ext.util.Numbers.Price.*;
import static com.hashnot.xchange.ext.util.Numbers.eq;
import static com.hashnot.xchange.ext.util.Numbers.lt;
import static com.hashnot.xchange.ext.util.Orders.*;
import static com.xeiam.xchange.dto.Order.OrderType;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;

/**
 * @author Rafał Krupiński
 */
public class Dealer implements IOrderBookSideListener, IBestOfferListener {
    private final IOrderUpdater orderUpdater;
    final private IOrderBookSideMonitor orderBookSideMonitor;

    public Simulation simulation;

    // side -> map(market -> best offer price)
    final private Map<MarketSide, BigDecimal> bestOffers = new HashMap<>();

    final private Map<ListingSide, Market> bestMarkets = new HashMap<>();

    final private Map<ListingSide, Market> worstMarkets = new HashMap<>();

    public Dealer(Simulation simulation, IOrderUpdater orderUpdater, IOrderBookSideMonitor orderBookSideMonitor) {
        this.simulation = simulation;
        this.orderUpdater = orderUpdater;
        this.orderBookSideMonitor = orderBookSideMonitor;
    }

    /**
     * If the new price is the best, update internal state and change order book monitor to monitor new best market
     */
    @Override
    public void updateBestOffer(BestOfferEvent evt) {
        log.info("Best offer {}", evt);

        Market market = evt.source.market;
        OrderType side = evt.source.side;
        if (!hasMinimumMoney(market, side, evt.price)) {
            log.debug("Ignore exchange without {} enough money for {}", evt.source, side);
            return;
        }

        MarketSide mSide = new MarketSide(market, side);
        BigDecimal oldPrice = bestOffers.get(mSide);
        BigDecimal newPrice = evt.price;

        // don't update the order if the new 3rd party order is not better
        if (oldPrice != null && eq(newPrice, oldPrice)) {
            log.warn("Price didn't change");
            return;
        }

        ListingSide oKey = new ListingSide(market.listing, side);
        Market bestMarket = bestMarkets.get(oKey);
        boolean dirty = false;
        if (bestMarket != null) {
            if (!bestMarket.equals(market)) {
                BigDecimal bestMarketPrice = bestOffers.get(new MarketSide(bestMarket, side));

                // worse is better, because we buy cheaper
                if (isWorse(newPrice, bestMarketPrice, side)) {
                    dirty = updateBestMarket(bestMarket, market, oKey);
                }
            }
        } else if (!eq(newPrice, forNull(side))) {
            dirty = updateBestMarket(null, market, oKey);
        }

        bestOffers.put(mSide, newPrice);

        Market oldWorst = worstMarkets.get(oKey);
        if (oldWorst == null) {
            worstMarkets.put(oKey, market);
            dirty = true;
        } else {
            BigDecimal oldWorstPrice = bestOffers.getOrDefault(new MarketSide(oldWorst, side), forNull(side));
            if (isBetter(newPrice, oldWorstPrice, side)) {
                worstMarkets.put(oKey, market);
                dirty = true;
            }
        }

        if (dirty)
            orderUpdater.update(new OrderUpdateEvent(side));
    }

    private boolean updateBestMarket(Market oldBest, Market best, ListingSide key) {
        if (best.equals(oldBest)) {
            log.warn("Updating the best market with the same market");
            return false;
        }

        bestMarkets.put(key, best);
        MarketSide bestSide = new MarketSide(best, key.side);
        log.debug("Add OBL to {}", bestSide);
        orderBookSideMonitor.addOrderBookSideListener(this, bestSide);
        if (oldBest != null) {
            MarketSide oldBestSide = new MarketSide(oldBest, key.side);
            log.debug("Remove OBL from {}", oldBestSide);
            orderBookSideMonitor.removeOrderBookSideListener(this, oldBestSide);
        }
        return true;
    }

    /**
     * Called when order book changes on the best exchange
     */
    @Override
    public void orderBookSideChanged(OrderBookSideUpdateEvent evt) {
        MarketSide source = evt.source;
        log.info("order book from {}", source);

        CurrencyPair listing = source.market.listing;
        OrderType side = source.side;
        ListingSide listingSide = new ListingSide(listing, side);
        Market bestMarket = bestMarkets.get(listingSide);

        if (!bestMarket.equals(source.market)) {
            log.warn("Unexpected OrderBook update from {}", source);
            return;
        }

        Market worstMarket = worstMarkets.get(listingSide);
        if (bestMarket.equals(worstMarket)) {
            log.warn("Didn't find 2 exchanges with sufficient funds ({})", side);
            return;
        }

        //TODO change may mean that we need to change the open order to smaller one

        // open or update order
        IExchange exchange = source.market.exchange;
        List<LimitOrder> limited = OrderBooks.removeOverLimit(evt.newOrders, exchange.getLimit(listing.baseSymbol), exchange.getLimit(listing.counterSymbol));

        // TODO remove net prices
        Orders.updateNetPrices(limited, exchange.getMarketMetadata(source.market.listing).getOrderFeeFactor());

        MarketSide worstKey = new MarketSide(worstMarket, side);
        LimitOrder worstOrder = new LimitOrder.Builder(side, listing).limitPrice(bestOffers.get(worstKey)).build();
        Orders.updateNetPrice(worstOrder, worstMarket.exchange);

        LimitOrder bestOrder = Orders.closing(evt.newOrders.get(0), exchange);

        if (!isProfitable(worstOrder, bestOrder)) {
            orderUpdater.update(new OrderUpdateEvent(side));
            return;
        }

        OrderUpdateEvent event = simulation.deal(worstOrder, worstMarket.exchange, limited, exchange);
        if (event != null) {
            log.info("Place {}", event.openedOrder);
            orderUpdater.update(event);
        } else
            orderUpdater.update(new OrderUpdateEvent(side));
    }

    private boolean hasMinimumMoney(IExchange x, LimitOrder order, OrderType dir) {
        return hasMinimumMoney(new Market(x, order.getCurrencyPair()), dir, order.getLimitPrice());
    }

    private boolean hasMinimumMoney(Market market, OrderType dir, BigDecimal price) {
        BigDecimal outAmount = market.exchange.getWallet(Orders.outgoingCurrency(dir, market.listing));

        BigDecimal baseAmount;
        if (dir == ASK)
            baseAmount = outAmount;
        else {
            baseAmount = outAmount.divide(price, c);
        }

        return !lt(baseAmount, market.exchange.getMarketMetadata(market.listing).getAmountMinimum());
    }

    private void clearOrders(CurrencyPair pair) {
        log.debug("TODO clearOrders");
    }

    private void updateLimitOrders(CurrencyPair pair, Collection<IExchange> exchanges, OrderType type) {
        //podsumuj wszystkie oferty do
        // - limitu stanu konta
        // - limitu różnicy ceny

        NavigableMap<LimitOrder, IExchange> bestOrders = null;//getBestOffers(pair, exchanges, type);
        if (bestOrders.size() < 2) {
            clearOrders(pair);
            return;
        }

        // find best - closing orders
        Map.Entry<LimitOrder, IExchange> best = null;
        for (Map.Entry<LimitOrder, IExchange> e : bestOrders.entrySet()) {
            if (hasMinimumMoney(e.getValue(), e.getKey(), revert(type))) {
                best = e;
                break;
            }
        }

        if (best == null) {
            log.info("No exchange has sufficient funds to close {}", type);
            return;
        }

        // find worst - opening orders
        Map.Entry<LimitOrder, IExchange> worst = null;
        for (Map.Entry<LimitOrder, IExchange> e : bestOrders.descendingMap().entrySet()) {
            if (hasMinimumMoney(e.getValue(), e.getKey(), type)) {
                worst = e;
                break;
            }
        }

        if (worst == null) {
            log.info("No exchange has sufficient funds to open {}", type);
            return;
        } else if (worst == best) {
            log.info("Didn't find 2 exchanges with sufficient funds ({})", type);
            return;
        }

        LimitOrder worstOrder = worst.getKey();
        updateNetPrice(worstOrder, bestOrders.get(worstOrder));

        IExchange worstExchange = worst.getValue();
        IExchange bestExchange = best.getValue();

        LimitOrder closingBest = closing(best.getKey(), bestExchange);
        if (!isProfitable(worstOrder, closingBest)) {
            orderUpdater.update(new OrderUpdateEvent(type));
            return;
        }

        List<LimitOrder> closeOrders = null;//orderBooks.get(new Market(bestExchange, pair)).getOrders(type);
        OrderUpdateEvent event = simulation.deal(worstOrder, worstExchange, closeOrders, bestExchange);
        if (event != null)
            orderUpdater.update(event);
        else
            orderUpdater.update(new OrderUpdateEvent(type));
    }

    private static final Logger log = LoggerFactory.getLogger(Dealer.class);

    private static class ListingSide {
        private final CurrencyPair listing;
        private final OrderType side;

        public ListingSide(CurrencyPair listing, OrderType side) {
            this.listing = listing;
            this.side = side;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o != null && o instanceof ListingSide && equals((ListingSide) o);
        }

        private boolean equals(ListingSide listingSide) {
            return listing.equals(listingSide.listing) && side == listingSide.side;
        }

        @Override
        public int hashCode() {
            int result = listing.hashCode();
            result = 31 * result + side.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return side + "@" + listing;
        }
    }
}
