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

    final private Map<ListingSide, Market> closeMarkets = new HashMap<>();

    final private Map<ListingSide, Market> openMarkets = new HashMap<>();

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
        Market closeMarket = closeMarkets.get(oKey);
        boolean dirty = false;
        if (closeMarket != null) {
            if (!closeMarket.equals(market)) {
                BigDecimal closeMarketPrice = bestOffers.get(new MarketSide(closeMarket, side));

                // worse is better, because we buy cheaper
                if (isWorse(newPrice, closeMarketPrice, side)) {
                    dirty = updateCloseMarket(closeMarket, market, oKey);
                }
            }
        } else if (!eq(newPrice, forNull(side))) {
            dirty = updateCloseMarket(null, market, oKey);
        }

        bestOffers.put(mSide, newPrice);

        Market oldOpen = openMarkets.get(oKey);
        if (oldOpen == null) {
            openMarkets.put(oKey, market);
            dirty = true;
        } else {
            BigDecimal oldOpenPrice = bestOffers.getOrDefault(new MarketSide(oldOpen, side), forNull(side));
            if (isBetter(newPrice, oldOpenPrice, side)) {
                openMarkets.put(oKey, market);
                dirty = true;
            }
        }

        if (dirty)
            orderUpdater.update(new OrderUpdateEvent(side));
    }

    private boolean updateCloseMarket(Market oldClose, Market newClose, ListingSide key) {
        if (newClose.equals(oldClose)) {
            log.warn("Updating the close market with the same market");
            return false;
        }

        closeMarkets.put(key, newClose);
        MarketSide marketSide = new MarketSide(newClose, key.side);
        log.debug("Add OBL to {}", marketSide);
        orderBookSideMonitor.addOrderBookSideListener(this, marketSide);
        if (oldClose != null) {
            MarketSide oldCloseSide = new MarketSide(oldClose, key.side);
            log.debug("Remove OBL from {}", oldCloseSide);
            orderBookSideMonitor.removeOrderBookSideListener(this, oldCloseSide);
        }
        return true;
    }

    /**
     * Called when order book changes on the close exchange
     */
    @Override
    public void orderBookSideChanged(OrderBookSideUpdateEvent evt) {
        MarketSide source = evt.source;
        log.info("order book from {}", source);

        CurrencyPair listing = source.market.listing;
        OrderType side = source.side;
        ListingSide listingSide = new ListingSide(listing, side);
        Market closeMarket = closeMarkets.get(listingSide);

        if (!closeMarket.equals(source.market)) {
            log.warn("Unexpected OrderBook update from {}", source);
            return;
        }

        Market openMarket = openMarkets.get(listingSide);
        if (closeMarket.equals(openMarket)) {
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

        MarketSide openSide = new MarketSide(openMarket, side);
        LimitOrder openOrder = new LimitOrder.Builder(side, listing).limitPrice(bestOffers.get(openSide)).build();
        IExchangeMonitor openExchange = monitors.get(openMarket.exchange);
        Orders.updateNetPrice(openOrder, openExchange);

        LimitOrder closeOrder = Orders.closing(evt.newOrders.get(0), monitor);

        if (!isProfitable(openOrder, closeOrder)) {
            orderUpdater.update(new OrderUpdateEvent(side));
            return;
        }

        OrderUpdateEvent event = simulation.deal(openOrder, openMarket.exchange, limited, exchange);
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
