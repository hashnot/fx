package com.hashnot.fx.dealer;

import com.hashnot.fx.ext.*;
import com.hashnot.fx.framework.IOrderUpdater;
import com.hashnot.fx.framework.OrderUpdateEvent;
import com.hashnot.fx.framework.Simulation;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

import static com.hashnot.fx.util.Numbers.eq;
import static com.hashnot.fx.util.Numbers.lt;
import static com.hashnot.fx.util.Orders.*;
import static com.hashnot.fx.util.Orders.Price.isBetter;
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
        if (bestMarket != null) {
            BigDecimal bestMarketPrice = bestOffers.get(new MarketSide(bestMarket, side));

            if (isBetter(newPrice, bestMarketPrice, side)) {
                updateBestMarket(bestMarket, market, oKey);
            }
        } else if (!eq(newPrice, Price.forNull(side))) {
            updateBestMarket(null, market, oKey);
        }

        bestOffers.put(mSide, newPrice);

        //TODO if best or worst market changes, cancel order
    }

    private void updateBestMarket(Market oldBest, Market best, ListingSide key) {
        if (oldBest == best) {
            log.warn("Updating the best market with the same market");
            return;
        }

        bestMarkets.put(key, best);
        orderBookSideMonitor.addOrderBookSideListener(this, new MarketSide(best, key.side));
        if (oldBest != null)
            orderBookSideMonitor.removeOrderBookSideListener(this, new MarketSide(oldBest, key.side));
    }

    /**
     * Called when order book changes on the best exchange
     */
    @Override
    public void orderBookSideChanged(OrderBookSideUpdateEvent evt) {
        log.info("order book from {}", evt.source);
        //TODO change may mean that we need to change the open order to smaller one

        // open or update order
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
    }
}
