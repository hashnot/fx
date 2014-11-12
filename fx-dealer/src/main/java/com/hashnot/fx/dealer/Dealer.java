package com.hashnot.fx.dealer;

import com.hashnot.fx.framework.*;
import com.hashnot.fx.util.OrderBooks;
import com.hashnot.xchange.event.IExchangeMonitor;
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
import static com.hashnot.xchange.ext.util.Numbers.*;
import static com.hashnot.xchange.ext.util.Orders.c;
import static com.hashnot.xchange.ext.util.Orders.revert;
import static com.xeiam.xchange.dto.Order.OrderType;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class Dealer implements IOrderBookSideListener, IBestOfferListener {
    static private final BigDecimal TWO = new BigDecimal(2);

    private final OrderManager orderManager;
    final private IOrderBookSideMonitor orderBookSideMonitor;

    final private IOrderTracker orderTracker;

    final private Map<Exchange, IExchangeMonitor> monitors;

    // side -> map(exchange -> best offer price)
    final private Map<Exchange, BigDecimal> bestOffers = new HashMap<>();

    private Exchange closeExchange;

    private Exchange openExchange;

    final private OrderType side;

    final private CurrencyPair listing;

    final private SimpleOrderOpenStrategy orderStrategy;

    public Dealer(IUserTradeMonitor userTradeMonitor, IOrderBookSideMonitor orderBookSideMonitor, IOrderTracker orderTracker, Map<Exchange, IExchangeMonitor> monitors, OrderType side, CurrencyPair listing, SimpleOrderOpenStrategy orderStrategy) {
        this.orderBookSideMonitor = orderBookSideMonitor;
        this.orderTracker = orderTracker;
        this.monitors = monitors;
        this.side = side;
        this.listing = listing;
        this.orderStrategy = orderStrategy;
        this.orderManager = new OrderManager(userTradeMonitor);
    }

    /**
     * If the new price is the best, update internal state and change order book monitor to monitor new best market
     */
    @Override
    public void updateBestOffer(BestOfferEvent evt) {
        assert side == evt.source.side;
        assert listing.equals(evt.source.market.listing);

        log.info("Best offer {}", evt);

        Exchange eventExchange = evt.source.market.exchange;
        if (!hasMinimumMoney(eventExchange, evt.price)) {
            log.debug("Ignore exchange {} without enough money", evt.source);
            return;
        }

        BigDecimal newPrice = evt.price;

        BigDecimal oldPrice = bestOffers.get(eventExchange);

        // don't update the order if the new 3rd party order is not better
        if (oldPrice != null && eq(newPrice, oldPrice)) {
            log.warn("Price didn't change");
            return;
        }

        if (isMineTop(eventExchange, newPrice)) return;

        bestOffers.put(eventExchange, newPrice);

        // it's important to first update open before close exchange,
        // because we register ourselves as a listener for close order book if close != open

        boolean dirty = false;
        if (!eventExchange.equals(openExchange)) {
            BigDecimal oldOpenPrice = forNull(revert(side));
            if (openExchange != null)
                oldOpenPrice = bestOffers.getOrDefault(openExchange, oldOpenPrice);

            // worse is better, because we open between closer and further on the further exchange
            if (isFurther(newPrice, oldOpenPrice, side)) {
                openExchange = eventExchange;
                dirty = true;
            }
        }

        if (!eventExchange.equals(closeExchange)) {
            BigDecimal closeMarketPrice = forNull(side);
            if (closeExchange != null)
                closeMarketPrice = bestOffers.getOrDefault(closeExchange, closeMarketPrice);

            if (isCloser(newPrice, closeMarketPrice, side)) {
                updateCloseMarket(eventExchange);
                dirty = true;
            }
        }

        if (dirty && orderManager.isActive())
            orderManager.cancel();
    }

    private void updateCloseMarket(Exchange newClose) {
        if (closeExchange != null) {
            MarketSide oldCloseSide = new MarketSide(closeExchange, listing, side);
            log.debug("Remove OBL from {}", oldCloseSide);
            orderBookSideMonitor.removeOrderBookSideListener(this, oldCloseSide);
        }

        closeExchange = newClose;

        if (closeExchange != openExchange) {
            MarketSide marketSide = new MarketSide(newClose, listing, side);
            log.debug("Add OBL to {}", marketSide);
            orderBookSideMonitor.addOrderBookSideListener(this, marketSide);
        }
    }

    /**
     * Called when order book changes on the close exchange
     */
    @Override
    public void orderBookSideChanged(OrderBookSideUpdateEvent evt) {
        assert side == evt.source.side;
        assert listing.equals(evt.source.market.listing);

        // enable when we have at open!=close exchanges before listening to order books
        // TODO initial state assert !closeExchange.equals(openExchange) : "Didn't find 2 exchanges with sufficient funds " + side;
        if (closeExchange.equals(openExchange)) {
            log.info("Didn't find 2 exchanges with sufficient funds {}", side);
            return;
        }

        log.info("order book from {}", evt.source);

        if (!evt.source.market.exchange.equals(closeExchange)) {
            log.warn("Unexpected OrderBook update from {}", evt.source);
            return;
        }

        //TODO change may mean that we need to change the open order to smaller one

        // open or update order
        IExchangeMonitor closeMonitor = monitors.get(closeExchange);
        List<LimitOrder> limited = OrderBooks.removeOverLimit(evt.newOrders, getLimit(closeMonitor, listing.baseSymbol), getLimit(closeMonitor, listing.counterSymbol));

        // TODO remove net prices
        Orders.updateNetPrices(limited, closeMonitor.getMarketMetadata(listing).getOrderFeeFactor());

        {
            // return if there is open and profitable order
            LimitOrder openOrder = getOpenOrder();
            if (openOrder != null)
                if (openOrderStillProfitable(openOrder, evt))
                    return;
        }

        LimitOrder closeOrder = Orders.closing(evt.newOrders.get(0), closeMonitor);

        BigDecimal openGrossPrice = bestOffers.get(openExchange);
        BigDecimal openNetPrice = getOpenNetPrice(openGrossPrice);
        BigDecimal diff = openNetPrice.subtract(closeOrder.getNetPrice());

        // ask -> diff > 0
        boolean profitable = isFurther(diff, ZERO, side);

        IExchangeMonitor openMonitor = monitors.get(openExchange);
        if (profitable) {
            int scale = openMonitor.getMarketMetadata(listing).getPriceScale();
            // TODO use getAmount
            openGrossPrice = orderStrategy.getPrice(openGrossPrice, diff, scale, side);
            openNetPrice = getOpenNetPrice(openGrossPrice);
        }
        LimitOrder openOrder = new LimitOrder.Builder(side, listing).limitPrice(openGrossPrice).build();
        openOrder.setNetPrice(openNetPrice);


        log.debug("open {} {} {} <=> {} {} close {}profitable", openOrder.getType(), openGrossPrice, openNetPrice, closeOrder.getNetPrice(), closeOrder.getLimitPrice(), profitable ? "" : "not ");
        if (!profitable) {
            if (orderManager.isActive())
                orderManager.cancel();
            return;
        }

        OrderBinding event = DealerHelper.deal(openOrder, openMonitor, limited, closeMonitor);
        if (event != null) {
            log.info("Place {}", event.openedOrder);
            orderManager.update(event);
        } else if (orderManager.isActive())
            orderManager.cancel();
    }

    protected static BigDecimal getLimit(IExchangeMonitor monitor, String currency) {
        return monitor.getWalletMonitor().getWallet(currency).multiply(TWO);
    }

    private boolean openOrderStillProfitable(LimitOrder openOrder, OrderBookSideUpdateEvent evt) {
        // working on a limited OrderBooks,
        if (evt.getChanges().isEmpty())
            return true;
        BigDecimal closeAmount = DealerHelper.getCloseAmount(evt.newOrders, openOrder, monitors.get(closeExchange));
        return !gt(closeAmount, openOrder.getTradableAmount());
    }

    protected boolean isMineTop(Exchange exchange, BigDecimal newTopPrice) {
        LimitOrder order = getOpenOrder();
        log.info("Best offer {}@{}/{}; mine {}", newTopPrice, side, exchange, order);
        return (order != null && isCloser(order.getLimitPrice(), newTopPrice, side));
    }

    private LimitOrder getOpenOrder() {
        // 0 or one iterations
        for (LimitOrder order : orderTracker.getMonitored(openExchange).values())
            if (order.getType() == side)
                return order;
        return null;
    }

    private BigDecimal getOpenNetPrice(BigDecimal openGrossPrice) {
        return Orders.getNetPrice(openGrossPrice, side, monitors.get(openExchange).getMarketMetadata(listing).getOrderFeeFactor());
    }

    private boolean hasMinimumMoney(Exchange exchange, BigDecimal price) {
        IExchangeMonitor monitor = monitors.get(exchange);
        BigDecimal outAmount = monitor.getWalletMonitor().getWallet(Orders.outgoingCurrency(side, listing));
        BigDecimal amountMinimum = monitor.getMarketMetadata(listing).getAmountMinimum();

        BigDecimal baseAmount;
        if (side == ASK)
            baseAmount = outAmount;
        else {
            baseAmount = outAmount.divide(price, amountMinimum.scale(), c.getRoundingMode());
        }

        return !lt(baseAmount, amountMinimum);
    }

    private static final Logger log = LoggerFactory.getLogger(Dealer.class);

}
