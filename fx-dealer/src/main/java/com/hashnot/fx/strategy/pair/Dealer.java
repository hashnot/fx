package com.hashnot.fx.strategy.pair;

import com.google.common.collect.Ordering;
import com.hashnot.fx.framework.BestOfferEvent;
import com.hashnot.fx.framework.IOrderBookSideMonitor;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.framework.OrderBookSideUpdateEvent;
import com.hashnot.xchange.async.impl.AsyncSupport;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.hashnot.xchange.ext.util.Collections;
import com.hashnot.xchange.ext.util.Comparators;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.hashnot.fx.strategy.pair.DealerHelper.hasMinimumMoney;
import static com.hashnot.xchange.ext.util.BigDecimals.TWO;
import static com.hashnot.xchange.ext.util.Comparables.eq;
import static com.hashnot.xchange.ext.util.Comparables.lt;
import static com.hashnot.xchange.ext.util.Orders.*;
import static com.hashnot.xchange.ext.util.Prices.isFurther;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static java.math.BigDecimal.ONE;
import static java.math.RoundingMode.FLOOR;
import static java.math.RoundingMode.HALF_EVEN;

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

    private Comparator<Map.Entry<Exchange, BigDecimal>> offerComparator;

    public void updateBestOffers(Collection<BestOfferEvent> bestOfferEvents) {
        for (BestOfferEvent bestOfferEvent : bestOfferEvents)
            updateBestOffer(bestOfferEvent);
    }

    /**
     * If the new price is the best, update internal state and change order book monitor to monitor new best market
     */
    protected void updateBestOffer(BestOfferEvent evt) {
        assertConfig(evt.source.side, evt.source.market.listing);
        updateBestOffer(evt.source.market.exchange, evt.price);
    }

    protected void updateBestOffer(Exchange eventExchange, BigDecimal newPrice) {
        if (!hasMinimumMoney(monitors.get(eventExchange), newPrice, config)) {
            log.debug("Ignore exchange {} without enough money", eventExchange);
            return;
        }

        BigDecimal oldPrice = data.getBestOffers().put(eventExchange, newPrice);

        // don't update the order if the new 3rd party order is not better
        if (eq(oldPrice, newPrice)) {
            log.warn("Price didn't change");
            return;
        }

        IExchangeMonitor openMonitor = data.getOpenExchange();
        IExchangeMonitor closeMonitor = data.getCloseExchange();
        IExchangeMonitor eventMonitor = monitors.get(eventExchange);
        log.info("Best offer {} @{}/{}; mine {}@{}", newPrice, config.side, eventExchange, data.getOpenPrice(), openMonitor);

        // if the open exchange has changed - cancel
        // if close - check profitability

        // it's important to first update open before close exchange,
        // because we register ourselves as a listener for close order book if close != open

        Collections.MinMax<Map.Entry<Exchange, BigDecimal>> minMax = Collections.minMax(data.getBestOffers().entrySet(), offerComparator);

        Exchange newOpenExchange = minMax.min.getKey();
        data.setOpenExchange(monitors.get(newOpenExchange));

        Exchange newCloseExchange = minMax.max.getKey();
        data.setCloseExchange(monitors.get(newCloseExchange));

        if (data.getOpenExchange().equals(data.getCloseExchange())) {
            log.info("open = close = {}", eventExchange);
            cancel(openMonitor);
            //return;
        }

        if (!data.getCloseExchange().equals(closeMonitor))
            updateCloseMarket(closeMonitor);

        if (!data.getOpenExchange().equals(openMonitor)) {
            if (data.state == DealerState.Waiting) {
                cancel(openMonitor);
                openIfProfitable();
            }
        } else if (!data.getCloseExchange().equals(closeMonitor)) {
            cancel(openMonitor);
            // cannot open because we don't have current order book from the closing exchange
        } else {
            // only if eventExchange == openExchange
            if (data.state == DealerState.Waiting && openMonitor.equals(eventMonitor)) {
                if (isFurther(data.openedOrder.getLimitPrice(), newPrice, config.side)) {
                    log.debug("My order fell from the top");
                    cancel(openMonitor);
                    openIfProfitable();
                } else {
                    log.debug("Mine on top");
                }
            }
        }
    }

    private void updateCloseMarket(IExchangeMonitor oldCloseMonitor) {
        IExchangeMonitor newCloseMonitor = data.getCloseExchange();
        IExchangeMonitor newOpenMonitor = data.getOpenExchange();
        log.debug("open {} old close {} new close {}", newOpenMonitor, oldCloseMonitor, newCloseMonitor);
        if (oldCloseMonitor != null && (
                !oldCloseMonitor.equals(newCloseMonitor)
                        || newCloseMonitor.equals(newOpenMonitor))
                ) {
            MarketSide oldCloseSide = new MarketSide(oldCloseMonitor.getExchange(), config.listing, config.side);
            log.debug("Remove OBL from {}", oldCloseSide);
            orderBookSideMonitor.removeOrderBookSideListener(listener, oldCloseSide);
        }

        if (!newCloseMonitor.equals(newOpenMonitor)) {
            MarketSide marketSide = new MarketSide(newCloseMonitor.getExchange(), config.listing, config.side);
            log.debug("Add OBL to {}", marketSide);
            orderBookSideMonitor.addOrderBookSideListener(listener, marketSide);
        }
    }

    /**
     * Called when order book changes on the close exchange
     */
    public void orderBookSideChanged(OrderBookSideUpdateEvent evt) {
        assertConfig(evt.source.side, evt.source.market.listing);

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
                if (!profitableOrCancel(data.getOpenExchange()))
                    openIfProfitable();
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

    /**
     * @return true if profitable, false if not profitable (and cancelled) or no opened order
     */
    private boolean profitableOrCancel(IExchangeMonitor exchange) {
        if (data.state != DealerState.Waiting) {
            log.debug("No order");
            return false;
        } else if (data.checkProfitable()) {
            log.debug("Order still profitable");
            return true;
        } else {
            log.info("Unprofitable {}", data.openedOrder);
            cancel(exchange);
            return false;
        }
    }

    protected void openIfProfitable() {
        BigDecimal openPrice = profitableOpenPrice();
        if (openPrice == null)
            return;

        LimitOrder open = deal(openPrice);
        if (open != null) {
            log.info("Place {} @{}", open, data.getOpenExchange());
            data.openedOrder = open;
            open();
        }
    }

    BigDecimal profitableOpenPrice() {
        IExchangeMonitor openMonitor = data.getOpenExchange();
        BigDecimal openBest = data.getBestOffers().get(openMonitor.getExchange());
        BigDecimal closeBest = data.getBestOffers().get(data.getCloseExchange().getExchange());
        MarketMetadata openMeta = openMonitor.getMarketMetadata(config.listing);
        MarketMetadata closeMeta = data.getCloseExchange().getMarketMetadata(config.listing);

        //BigDecimal openInitial = openBest.add(openMeta.getPriceStep().multiply(bigFactor(revert(config.side))));

        BigDecimal closeNet = getNetPrice(closeBest, revert(config.side), closeMeta.getOrderFeeFactor()).setScale(closeMeta.getPriceScale(), HALF_EVEN);
        BigDecimal openNet = getNetPrice(openBest, config.side, closeMeta.getOrderFeeFactor()).setScale(closeMeta.getPriceScale(), HALF_EVEN);

        BigDecimal diff = openBest.subtract(closeBest);

        // ask -> diff > 0
        boolean profitable = isFurther(openNet, closeNet, config.side);

        log.info("gross open {} {} {} <=> {} {} close {}profitable", config.side, openBest, openNet, closeNet, closeBest, profitable ? "" : "not ");
        if (!profitable) {
            return null;
        }

        BigDecimal factor;
        if (config.side == ASK)
            factor = ONE.subtract(openMeta.getOrderFeeFactor()).divide(ONE.add(closeMeta.getOrderFeeFactor()), openMeta.getPriceScale(), HALF_EVEN);
        else
            factor = ONE.add(closeMeta.getOrderFeeFactor()).divide(ONE.subtract(openMeta.getOrderFeeFactor()), openMeta.getPriceScale(), HALF_EVEN);
        BigDecimal priceCloseInitial = openBest.multiply(factor).setScale(openMeta.getPriceScale(), HALF_EVEN);

        BigDecimal priceOpen = orderStrategy.getPrice(priceCloseInitial, diff, openMeta.getPriceScale(), config.side);

        BigDecimal myOpen = priceOpen.divide(factor, openMeta.getPriceScale(), HALF_EVEN);

        BigDecimal myOpenNet = getNetPrice(priceOpen, config.side, openMeta.getOrderFeeFactor()).setScale(openMeta.getPriceScale(), HALF_EVEN);

        profitable = isFurther(myOpenNet, closeNet, config.side);
        log.info("open {} {} {} <=> {} {} close {}profitable", config.side, myOpen, myOpenNet, closeNet, closeBest, profitable ? "" : "not ");

        return profitable ? myOpen : null;
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

    protected boolean cancel(IExchangeMonitor exchange) {
        switch (data.state) {
            case Waiting:
                doCancel(exchange);
                return true;
            case Opening:
                log.warn("Cancel while opening");
                onOpen = () -> doCancel(exchange);
                return true;
            case Cancelling:
            case NotProfitable:
                log.info("Cancel while in state: {}", data.state);
                return false;
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

    public LimitOrder deal(BigDecimal limitPrice) {

        //after my open order is closed at the open exchange, this money should disappear
        String openOutgoingCur = config.outgoingCurrency();

        String closeOutCur = config.incomingCurrency();

        Map<String, BigDecimal> openWallet = data.getOpenExchange().getWalletMonitor().getWallet();
        Map<String, BigDecimal> closeWallet = data.getCloseExchange().getWalletMonitor().getWallet();

        BigDecimal openOutGross = openWallet.get(openOutgoingCur);
        BigDecimal closeOutGross = closeWallet.get(closeOutCur);

        MarketMetadata openMetadata = data.getOpenExchange().getMarketMetadata(config.listing);
        MarketMetadata closeMetadata = data.getCloseExchange().getMarketMetadata(config.listing);

        // adjust amounts by dividing by two and check if it's not below minima
        {
            // amount of base currency
            BigDecimal openBaseAmount = openWallet.get(config.listing.baseSymbol);
            BigDecimal closeBaseAmount = closeWallet.get(config.listing.baseSymbol);

            BigDecimal openBaseHalf = openBaseAmount.divide(TWO, FLOOR);
            BigDecimal closeBaseHalf = closeBaseAmount.divide(TWO, FLOOR);

            BigDecimal openLowLimit = openMetadata.getAmountMinimum();
            BigDecimal closeLowLimit = closeMetadata.getAmountMinimum();

            // if half of the wallet is greater than minimum, open for half, if less, open for full amount but only if it's ASK (arbitrary, avoid wallet collision)
            if (lt(openBaseHalf, openLowLimit) || lt(closeBaseHalf, closeLowLimit)) {
                if (config.side == Order.OrderType.BID) {
                    log.info("Skip BID to avoid wallet collision over BID/ASK");
                    return null;
                }
            } else {
                openOutGross = openOutGross.divide(TWO, FLOOR);
                closeOutGross = closeOutGross.divide(TWO, FLOOR);
            }
        }

        log.debug("type: {}, open {}, close {}", config.side, data.getOpenExchange(), data.getCloseExchange());
        log.debug("open  {} {}", openOutgoingCur, openOutGross);
        log.debug("close {} {}", closeOutCur, closeOutGross);

        // limit po stronie open ASK = base wallet
        //                  close/ BID = suma( otwarte ASK-> cena*liczba) < base wallet
        //                  openOut = base
        //                  closeOut = counter
        //                  -- OR --
        //                  open BID = counter wallet/cena
        //                  close/ ASK = suma (otwarte BID -> liczba) < base wallet
        //                  openOut = counter
        //                  closeOut = base

        BigDecimal openAmount = openOutGross;
        if (config.side == Order.OrderType.BID)
            openAmount = openOutGross.divide(limitPrice, openWallet.get(closeOutCur).scale(), HALF_EVEN);

        BigDecimal closeAmount = data.getCloseAmount(config.side, openAmount, limitPrice);

        log.debug("open: {}", openAmount);
        log.debug("available: {}", closeAmount);
        log.debug("close: {}", closeOutGross);

        BigDecimal openAmountActual = Ordering.natural().min(openAmount, closeAmount, closeOutGross).setScale(DealerHelper.getScale(openMetadata, closeMetadata), FLOOR);

        if (!DealerHelper.checkMinima(openAmountActual, openMetadata)) {
            log.debug("Amount {} less than minimum", openAmountActual);
            return null;
        }

        openAmountActual = orderStrategy.getAmount(openAmountActual, closeMetadata.getAmountMinimum());
        return new LimitOrder.Builder(config.side, config.listing).limitPrice(limitPrice).tradableAmount(openAmountActual).build();
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
        offerComparator = new Comparators.MultiplyingComparator<>(new Comparators.EntryValueComparator<>(), -factor(config.side));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public String toString() {
        return "Dealer:" + config;
    }
}
