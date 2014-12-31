package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.BestOfferEvent;
import com.hashnot.fx.framework.IOrderBookSideMonitor;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.framework.OrderBookSideUpdateEvent;
import com.hashnot.xchange.async.impl.AsyncSupport;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.hashnot.fx.strategy.pair.DealerHelper.hasMinimumMoney;
import static com.hashnot.xchange.ext.util.Comparables.eq;
import static com.hashnot.xchange.ext.util.Orders.getNetPrice;
import static com.hashnot.xchange.ext.util.Orders.revert;
import static com.hashnot.xchange.ext.util.Prices.*;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class Dealer {
    final private static Logger log = LoggerFactory.getLogger(Dealer.class);

    final private IOrderBookSideMonitor orderBookSideMonitor;
    private Listener listener;

    final private Map<Exchange, IExchangeMonitor> monitors;

    final private SimpleOrderOpenStrategy orderStrategy;
    final private SimpleOrderCloseStrategy orderCloseStrategy;

    final private DealerData data;

    final private DealerConfig config;

    public void updateBestOffers(Collection<BestOfferEvent> bestOfferEvents) {
        for (BestOfferEvent bestOfferEvent : bestOfferEvents)
            updateBestOffer(bestOfferEvent);
    }

    /**
     * If the new price is the best, update internal state and change order book monitor to monitor new best market
     */
    private void updateBestOffer(BestOfferEvent evt) {
        assertConfig(evt.source.side, evt.source.market.listing);

        Exchange eventExchange = evt.source.market.exchange;
        if (!hasMinimumMoney(monitors.get(eventExchange), evt.price, config)) {
            log.debug("Ignore exchange {} without enough money", evt.source);
            return;
        }

        BigDecimal newPrice = evt.price;

        BigDecimal oldPrice = data.getBestOffers().get(eventExchange);

        // don't update the order if the new 3rd party order is not better
        if (oldPrice != null && eq(newPrice, oldPrice)) {
            log.warn("Price didn't change");
            return;
        }

        data.getBestOffers().put(eventExchange, newPrice);

        IExchangeMonitor openMonitor = data.getOpenExchange();
        IExchangeMonitor eventMonitor = monitors.get(eventExchange);
        log.info("Best offer {} @{}/{}; mine {}@{}", newPrice, config.side, eventExchange, data.getOpenPrice(), openMonitor);

        // if the open exchange has changed - cancel
        // if close - check profitability
        boolean update = false;
        {
            if (data.state == DealerState.Waiting && openMonitor.equals(eventMonitor)) {
                if (isFurther(data.openedOrder.getLimitPrice(), newPrice, config.side)) {
                    log.debug("My order fell from the top");
                    update = true;
                } else {
                    log.debug("Mine on top");
                    return;
                }
            }
        }

        // it's important to first update open before close exchange,
        // because we register ourselves as a listener for close order book if close != open

        boolean dirty = false;
        if (!eventMonitor.equals(openMonitor)) {
            BigDecimal oldOpenPrice = forNull(revert(config.side));
            if (openMonitor != null)
                oldOpenPrice = data.getBestOffers().getOrDefault(openMonitor.getExchange(), oldOpenPrice);

            // worse is better, because we open between closer and further on the further exchange
            if (isFurther(newPrice, oldOpenPrice, config.side)) {
                data.setOpenExchange(eventMonitor);
                dirty = true;
            }
        }

        IExchangeMonitor oldClose = data.getCloseExchange();

        if (!eventMonitor.equals(data.getCloseExchange())) {
            BigDecimal closeMarketPrice = forNull(config.side);
            if (data.getCloseExchange() != null)
                closeMarketPrice = data.getBestOffers().getOrDefault(data.getCloseExchange().getExchange(), closeMarketPrice);

            if (isCloser(newPrice, closeMarketPrice, config.side)) {
                data.setCloseExchange(eventMonitor);
                dirty = true;
            }
        }


        if (dirty) {
            updateCloseMarket(oldClose);
            if (data.state == DealerState.Waiting) {
                // if current event is from open, may open new order.
                // if it's from close, must cancel, because don't know current order book
                cancel(openMonitor);
                if (eventMonitor.equals(data.getOpenExchange()))
                    openIfProfitable();
                return;
            }
        }
        if (update) {
            cancel(openMonitor);
            openIfProfitable();
        }
    }

    private void updateCloseMarket(IExchangeMonitor oldExchange) {
        log.debug("open {} old close {} new close {}", data.getOpenExchange(), oldExchange, data.getCloseExchange());
        if (oldExchange != null && !oldExchange.equals(data.getCloseExchange())) {
            MarketSide oldCloseSide = new MarketSide(oldExchange.getExchange(), config.listing, config.side);
            log.debug("Remove OBL from {}", oldCloseSide);
            orderBookSideMonitor.removeOrderBookSideListener(listener, oldCloseSide);
        }

        if (!data.getCloseExchange().equals(data.getOpenExchange())) {
            MarketSide marketSide = new MarketSide(data.getCloseExchange().getExchange(), config.listing, config.side);
            log.debug("Add OBL to {}", marketSide);
            orderBookSideMonitor.addOrderBookSideListener(listener, marketSide);
        }
    }

    /**
     * Called when order book changes on the close exchange
     */
    public void orderBookSideChanged(OrderBookSideUpdateEvent evt) {
        assertConfig(evt.source.side, evt.source.market.listing);

        // enable when we have at open!=close exchanges before listening to order books
        // TODO initial state assert !getCloseExchange().equals(getOpenExchange()) : "Didn't find 2 exchanges with sufficient funds " + side;
        if (data.getCloseExchange().equals(data.getOpenExchange())) {
            log.info("Didn't find 2 exchanges with sufficient funds {}", config.side);
            return;
        }

        if (!evt.source.market.exchange.equals(data.getCloseExchange().getExchange())) {
            log.warn("Unexpected OrderBook update from {}", evt.source);
            return;
        }

        log.debug("order book from {}", evt.source);

        // working on a limited OrderBooks,
        if (evt.getChanges().isEmpty()) {
            log.debug("Monitored part of the order book did not change.");
            return;
        }

        data.closingOrders = evt.newOrders;

        switch (data.state) {
            case Waiting:
                //TODO change may mean that we need to change the open order to smaller one
                profitableOrCancel(data.getOpenExchange());
                break;
            case NotProfitable:
                openIfProfitable();
                break;
            default:
                log.warn("Dealer {}", data.state);
        }
    }

    public void trades(List<UserTradeEvent> tradeEvents) {
        for (UserTradeEvent tradeEvent : tradeEvents)
            trade(tradeEvent);
    }

    /**
     * There is no filtering of side nor listing
     */
    private void trade(UserTradeEvent evt) {
        UserTrade trade = evt.trade;

        if (!checkConfig(trade.getType(), trade.getCurrencyPair()))
            return;

        if (data.state != DealerState.Waiting || !trade.getOrderId().equals(data.openOrderId)) {
            log.debug("Trade of an unknown order {} @{}", trade, evt.source);
            return;
        }

        if (!evt.source.equals(data.openExchange.getExchange())) {
            log.warn("Trade of untracked exchange {}@{}", trade, evt.source);
            return;
        }

        orderCloseStrategy.placeOrder(trade.getTradableAmount(), data.closingOrders, data.getCloseExchange().getAsyncExchange().getTradeService());

        if (evt.current == null) {
            data.openExchange.getOrderTracker().removeTradeListener(listener);
            data.state = DealerState.NotProfitable;
        }
    }

    private void profitableOrCancel(IExchangeMonitor exchange) {
        if (data.checkProfitable()) {
            log.debug("Order still profitable");
        } else {
            log.info("Unprofitable {}", data.openedOrder);
            cancel(exchange);
            openIfProfitable();
        }
    }

    protected void openIfProfitable() {
        BigDecimal closingNetPrice;
        BigDecimal closingPrice;
        {
            LimitOrder closeOrder = data.closingOrders.get(0);
            closingPrice = closeOrder.getLimitPrice();
            closingNetPrice = getNetPrice(closeOrder.getLimitPrice(), revert(closeOrder.getType()), data.getCloseExchange().getMarketMetadata(closeOrder.getCurrencyPair()).getOrderFeeFactor());
        }

        BigDecimal openGrossPrice = data.getBestOffers().get(data.getOpenExchange().getExchange());
        BigDecimal openNetPrice = getOpenNetPrice(openGrossPrice);
        BigDecimal diff = openNetPrice.subtract(closingNetPrice);

        // ask -> diff > 0
        boolean profitable = isFurther(diff, ZERO, config.side);

        IExchangeMonitor openMonitor = monitors.get(data.getOpenExchange().getExchange());
        if (profitable) {
            int scale = openMonitor.getMarketMetadata(config.listing).getPriceScale();
            // TODO use getAmount
            openGrossPrice = orderStrategy.getPrice(openGrossPrice, diff, scale, config.side);
            openNetPrice = getOpenNetPrice(openGrossPrice);
        }

        log.info("open {} {} {} <=> {} {} close {}profitable", config.side, openGrossPrice, openNetPrice, closingNetPrice, closingPrice, profitable ? "" : "not ");
        if (!profitable)
            return;

        LimitOrder open = DealerHelper.deal(config, data, openGrossPrice, orderStrategy);
        if (open != null) {
            log.info("Place {} @{}", open, openMonitor);
            data.openedOrder = open;
            open();
        }
    }

    Runnable onOpen;

    public void open() {
        data.state = DealerState.Opening;

        IAsyncTradeService tradeService = data.openExchange.getAsyncExchange().getTradeService();
        tradeService.placeLimitOrder(data.openedOrder, (idf) -> {
            try {
                data.openOrderId = AsyncSupport.get(idf);
                data.state = DealerState.Waiting;
                if (onOpen != null) {
                    onOpen.run();
                    onOpen = null;
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Error from {}", data.openExchange, e);
                data.state = DealerState.NotProfitable;
            }
        });
        data.openExchange.getOrderTracker().addTradeListener(listener);
    }

    protected void cancel(IExchangeMonitor exchange) {
        switch (data.state) {
            case Waiting:
                doCancel(exchange);
                break;
            case Opening:
                log.warn("Cancel while opening");
                onOpen = () -> doCancel(exchange);
                break;
            case Cancelling:
            case NotProfitable:
                log.info("Cancel while is state: {}", data.state);
                break;
            default:
                throw new IllegalStateException(data.state.name());
        }
    }

    private void doCancel(IExchangeMonitor exchange) {
        log.info("Cancel @{} {} {}", exchange, data.openOrderId, data.openedOrder);
        IAsyncTradeService tradeService = exchange.getAsyncExchange().getTradeService();
        String orderId = data.openOrderId;
        tradeService.cancelOrder(orderId, (future) -> {
            data.state = DealerState.NotProfitable;
            try {
                if (!future.get()) {
                    log.warn("No such order {}", orderId);
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted!", e);
            } catch (ExecutionException e) {
                log.warn("Error from {}", tradeService, e.getCause());
            }
        });
        data.state = DealerState.Cancelling;
    }

    private void assertConfig(Order.OrderType side, CurrencyPair listing) {
        if (config.side != side)
            throw new IllegalArgumentException(side.name());
        if (!config.listing.equals(listing))
            throw new IllegalArgumentException(listing.toString());
    }

    private boolean checkConfig(Order.OrderType side, CurrencyPair listing) {
        return config.side == side && config.listing.equals(listing);
    }

    public Dealer(IOrderBookSideMonitor orderBookSideMonitor, Map<Exchange, IExchangeMonitor> monitors, DealerConfig config, SimpleOrderOpenStrategy orderStrategy, SimpleOrderCloseStrategy orderCloseStrategy) {
        this(orderBookSideMonitor, monitors, config, orderStrategy, orderCloseStrategy, new DealerData());
    }

    protected Dealer(IOrderBookSideMonitor orderBookSideMonitor, Map<Exchange, IExchangeMonitor> monitors, DealerConfig config, SimpleOrderOpenStrategy orderStrategy, SimpleOrderCloseStrategy orderCloseStrategy, DealerData data) {
        this.orderBookSideMonitor = orderBookSideMonitor;
        this.monitors = monitors;
        this.config = config;
        this.orderStrategy = orderStrategy;
        this.orderCloseStrategy = orderCloseStrategy;
        this.data = data;
    }

    private BigDecimal getOpenNetPrice(BigDecimal openGrossPrice) {
        return getNetPrice(openGrossPrice, config.side, data.getOpenExchange().getMarketMetadata(config.listing).getOrderFeeFactor());
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public String toString() {
        return "Dealer:" + config;
    }
}
