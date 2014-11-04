package com.hashnot.fx.dealer;

import com.hashnot.fx.framework.*;
import com.hashnot.fx.util.OrderBooks;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.Market;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hashnot.xchange.ext.util.Numbers.Price.*;
import static com.hashnot.xchange.ext.util.Numbers.eq;
import static com.hashnot.xchange.ext.util.Numbers.lt;
import static com.hashnot.xchange.ext.util.Orders.c;
import static com.hashnot.xchange.ext.util.Orders.isProfitable;
import static com.xeiam.xchange.dto.Order.OrderType;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;

/**
 * @author Rafał Krupiński
 */
public class Dealer implements IOrderBookSideListener, IBestOfferListener {
    private final IOrderUpdater orderUpdater;
    final private IOrderBookSideMonitor orderBookSideMonitor;

    final private Simulation simulation;
    final private Map<Exchange, IExchangeMonitor> monitors;

    // side -> map(market -> best offer price)
    final private Map<MarketSide, BigDecimal> bestOffers = new HashMap<>();

    final private Map<ListingSide, Market> bestMarkets = new HashMap<>();

    final private Map<ListingSide, Market> worstMarkets = new HashMap<>();

    public Dealer(Simulation simulation, IOrderUpdater orderUpdater, IOrderBookSideMonitor orderBookSideMonitor, Map<Exchange, IExchangeMonitor> monitors) {
        this.simulation = simulation;
        this.orderUpdater = orderUpdater;
        this.orderBookSideMonitor = orderBookSideMonitor;
        this.monitors = monitors;
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
        Exchange exchange = source.market.exchange;
        IExchangeMonitor monitor = monitors.get(exchange);
        List<LimitOrder> limited = OrderBooks.removeOverLimit(evt.newOrders, monitor.getLimit(listing.baseSymbol), monitor.getLimit(listing.counterSymbol));

        // TODO remove net prices
        Orders.updateNetPrices(limited, monitor.getMarketMetadata(source.market.listing).getOrderFeeFactor());

        MarketSide worstKey = new MarketSide(worstMarket, side);
        LimitOrder worstOrder = new LimitOrder.Builder(side, listing).limitPrice(bestOffers.get(worstKey)).build();
        IExchangeMonitor worstExchange = monitors.get(worstMarket.exchange);
        Orders.updateNetPrice(worstOrder, worstExchange);

        LimitOrder bestOrder = Orders.closing(evt.newOrders.get(0), monitor);

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

    private boolean hasMinimumMoney(Market market, OrderType dir, BigDecimal price) {
        IExchangeMonitor monitor = monitors.get(market.exchange);
        BigDecimal outAmount = monitor.getWallet(Orders.outgoingCurrency(dir, market.listing));

        BigDecimal baseAmount;
        if (dir == ASK)
            baseAmount = outAmount;
        else {
            baseAmount = outAmount.divide(price, c);
        }

        return !lt(baseAmount, monitor.getMarketMetadata(market.listing).getAmountMinimum());
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
