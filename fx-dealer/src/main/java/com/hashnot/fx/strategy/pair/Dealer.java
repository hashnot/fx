package com.hashnot.fx.strategy.pair;

import com.google.common.collect.Ordering;
import com.hashnot.fx.framework.BestOfferEvent;
import com.hashnot.fx.framework.IOrderBookSideMonitor;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.framework.OrderBookSideUpdateEvent;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.hashnot.xchange.ext.Market;
import com.hashnot.xchange.ext.trade.OrderPlacementEvent;
import com.hashnot.xchange.ext.util.Comparators;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order.OrderType;
import com.xeiam.xchange.dto.meta.MarketMetaData;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.hashnot.xchange.ext.util.BigDecimals.TWO;
import static com.hashnot.xchange.ext.util.Comparables.eq;
import static com.hashnot.xchange.ext.util.Comparables.lt;
import static com.hashnot.xchange.ext.util.Orders.*;
import static com.hashnot.xchange.ext.util.Prices.isFurther;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
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

    // TODO remove
    public final OrderType skipSide = ASK;

    public void updateBestOffers(Collection<BestOfferEvent> bestOfferEvents) {
        // at least one best price has changed
        boolean dirty = false;

        for (BestOfferEvent bestOfferEvent : bestOfferEvents) {
            MarketSide source = bestOfferEvent.source;
            Market market = source.market;

            assertConfig(source.side, market.listing);

            BigDecimal newPrice = bestOfferEvent.price;
            BigDecimal oldPrice = data.getBestOffers().put(market.exchange, newPrice);

            // don't update the order if the new order is not better
            if (eq(oldPrice, newPrice)) {
                log.warn("offer didn't change");
            } else {
                dirty = true;
                log.info("new offer {}@{}", newPrice, source);
            }
        }

        if (dirty)
            updateExchanges();
        else
            log.warn("No offers changed");
    }

    /**
     * If the new price is the best, update internal state and change order book monitor to monitor new best market
     */
    protected void updateExchanges() {
        Map<Exchange, BigDecimal> bestOffers = data.getBestOffers();

        if (bestOffers.size() != monitors.size()) {
            log.debug("Initializing best offers");
            return;
        }

        Map<Exchange, BigDecimal> openCandidates = new HashMap<>();
        Map<Exchange, BigDecimal> closeCandidates = new HashMap<>();

        for (Map.Entry<Exchange, BigDecimal> e : bestOffers.entrySet()) {
            Exchange x = e.getKey();
            if (hasMinimumMoney(x, false)) {
                openCandidates.put(x, e.getValue());
            }

            if (hasMinimumMoney(x, true)) {
                closeCandidates.put(x, e.getValue());
            }
        }

        if (openCandidates.isEmpty() || closeCandidates.isEmpty()) {
            log.info("No exchange with minimum money @{}", config);
            cancel();
            return;
        }

        Exchange newOpenExchange = Collections.min(openCandidates.entrySet(), offerComparator).getKey();
        Exchange newCloseExchange = Collections.max(closeCandidates.entrySet(), offerComparator).getKey();

        IExchangeMonitor newOpenMonitor = monitors.get(newOpenExchange);
        IExchangeMonitor newCloseMonitor = monitors.get(newCloseExchange);

        IExchangeMonitor openMonitor = data.getOpenExchange();
        IExchangeMonitor closeMonitor = data.getCloseExchange();
        log.info("best offer {}@{} worst {}@{}",
                data.getOpenPrice(), openMonitor,
                bestOffers.get(newCloseExchange), newCloseExchange,
                bestOffers.get(newOpenExchange), newOpenExchange
        );

        // if the close exchange has changed - cancel
        // if open - check profitability

        if (newOpenExchange.equals(newCloseExchange)) {
            log.info("open = close = {}", newCloseExchange);
            cancel();
        } else if (!newOpenMonitor.equals(openMonitor)) {
            if (data.state != DealerState.NotProfitable) {
                log.debug("new open");
                update();
            }
        } else if (!newCloseMonitor.equals(closeMonitor)) {
            log.debug("new close");
            cancel();
            // cannot open because we don't have current order book from the closing exchange
        } else {
            // only if open and close exchange haven't changed
            if (data.state == DealerState.Waiting) {
                BigDecimal newPrice = bestOffers.get(data.getOpenExchange().getExchange());
                if (isFurther(data.openedOrder.getLimitPrice(), newPrice, config.side)) {
                    log.debug("My order fell from the top");
                    update();
                } else {
                    log.debug("Mine on top");
                }
            }
        }

        data.setOpenExchange(newOpenMonitor);
        data.setCloseExchange(newCloseMonitor);

        if (
            // exit initial state
                (closeMonitor != null && closeMonitor.equals(openMonitor) && !newCloseMonitor.equals(newOpenMonitor)) ||

                        !newCloseMonitor.equals(closeMonitor))
            updateCloseMarket(closeMonitor);
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

    protected boolean hasMinimumMoney(Exchange exchange, boolean revertSide) {
        IExchangeMonitor monitor = monitors.get(exchange);
        OrderType side = revertSide ? revert(config.side) : config.side;
        OrderType revSide = revert(side);

        String currency = DealerConfig.incomingCurrency(revSide, config.listing);
        BigDecimal outAmount = monitor.getWalletMonitor().getWallet(currency);
        BigDecimal amountMinimum = monitor.getMarketMetadata(config.listing).getMinimumAmount();
        BigDecimal price = data.getBestOffers().get(monitor.getExchange());

        BigDecimal baseAmount;
        if (side == ASK)
            baseAmount = outAmount;
        else {
            baseAmount = outAmount.divide(price, amountMinimum.scale(), HALF_EVEN);
        }

        return !lt(baseAmount, amountMinimum);
    }

    public void onOrderBookSideEvents(Collection<OrderBookSideUpdateEvent> events) {
        for (OrderBookSideUpdateEvent event : events)
            orderBookSideChanged(event);
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
                if (!profitableOrCancel())
                    openIfProfitable();
                break;
            case NotProfitable:
                openIfProfitable();
                break;
            default:
                log.warn("OrderBook in {} state", data.state);
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

        if (!checkConfig(trade.getCurrencyPair()))
            return;

        if (data.in(DealerState.NotProfitable)) {
            log.warn("Trade in {} state", data.state);
            return;
        }

        String tradeOrderId = trade.getOrderId();
        Exchange exchange = evt.source;
        if (exchange.equals(data.getOpenExchange().getExchange()) && tradeOrderId.equals(data.openOrderId)) {
            handleOpenTrade(evt);
        } else if (exchange.equals(data.getCloseExchange().getExchange()) && data.getClosingOrderIds().contains(tradeOrderId)) {
            handleClosingTrade(evt);
        } else
            log.debug("Trade of an unknown order {} @{}", trade, exchange);
    }

    protected void handleOpenTrade(UserTradeEvent evt) {
        orderCloseStrategy.placeOrder(evt.trade.getTradableAmount(), data.closingOrders, data.getCloseExchange().getTrackingTradeService());

        if (evt.current == null) {
            log.info("Order {}@{} filled", evt.monitored.getId(), evt.source);
            data.openExchange.getOrderTracker().removeTradeListener(listener);
            data.state = DealerState.NotProfitable;
        }
    }

    protected void handleClosingTrade(UserTradeEvent evt) {
        if (evt.current == null)
            data.getClosingOrderIds().remove(evt.trade.getOrderId());
    }

    /**
     * @return true if profitable, false if not profitable (and cancelled) or no opened order
     */
    private boolean profitableOrCancel() {
        if (data.state != DealerState.Waiting) {
            log.debug("No order");
            return false;
        } else if (data.checkProfitable()) {
            log.debug("Order still profitable");
            return true;
        } else {
            log.info("Unprofitable {}", data.openedOrder);
            cancel();
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
        IExchangeMonitor closeMon = data.getCloseExchange();

        BigDecimal openBest = data.getBestOffers().get(openMonitor.getExchange());
        BigDecimal closeBest = data.getBestOffers().get(closeMon.getExchange());

        MarketMetaData openMeta = openMonitor.getMarketMetadata(config.listing);
        MarketMetaData closeMeta = closeMon.getMarketMetadata(config.listing);

        //BigDecimal openInitial = openBest.add(openMeta.getPriceStep().multiply(bigFactor(revert(config.side))));

        BigDecimal closeFeeFactor = closeMeta.getTradingFee();
        BigDecimal closeNet = getNetPrice(closeBest, revert(config.side), closeFeeFactor).setScale(closeMeta.getPriceScale(), HALF_EVEN);
        BigDecimal openNet = getNetPrice(openBest, config.side, closeFeeFactor).setScale(closeMeta.getPriceScale(), HALF_EVEN);

        BigDecimal diff = openNet.subtract(closeNet);

        // ask -> diff > 0
        boolean profitable = isFurther(openNet, closeNet, config.side);

        log.info("gross open {} {} {} <=> {} {} close {}profitable", config.side, openBest, openNet, closeNet, closeBest, profitable ? "" : "not ");
        if (!profitable) {
            return null;
        }

        BigDecimal openFeeFactor = openMeta.getTradingFee();
        BigDecimal factor;
        int openPriceScale = openMeta.getPriceScale();
        if (config.is(ASK))
            factor = ONE.subtract(openFeeFactor).divide(ONE.add(closeFeeFactor), openPriceScale, HALF_EVEN);
        else
            factor = ONE.add(closeFeeFactor).divide(ONE.subtract(openFeeFactor), openPriceScale, HALF_EVEN);
        BigDecimal priceCloseInitial = openBest.multiply(factor).setScale(openPriceScale, HALF_EVEN);

        BigDecimal priceOpen = orderStrategy.getPrice(priceCloseInitial, diff, openPriceScale, config.side);

        BigDecimal myOpen = priceOpen.divide(factor, openPriceScale, HALF_EVEN);

        BigDecimal myOpenNet = getNetPrice(myOpen, config.side, openFeeFactor, openMeta);

        profitable = isFurther(myOpenNet, closeNet, config.side);
        log.info("open {} {} {} <=> {} {} close {}profitable", config.side, myOpen, myOpenNet, closeNet, closeBest, profitable ? "" : "not ");

        return profitable ? myOpen : null;
    }

    void update() {
        switch (data.state) {
            case NotProfitable:
                throw new IllegalStateException(DealerState.NotProfitable.name());
            case Opening:
                onOpen = this::update;
                break;
            case Cancelling:
                onCancel = () -> {
                    openIfProfitable();
                    onCancel = null;
                };
            case Waiting:
                cancel();
        }
    }

    Runnable onOpen;
    Runnable onCancel;

    public void open() {
        assert data.state == DealerState.NotProfitable;
        data.state = DealerState.Opening;
        data.openExchange.getTrackingTradeService().placeLimitOrder(data.openedOrder, listener);
    }

    public void handleOpened(OrderPlacementEvent idf) {
        assert data.state == DealerState.Opening;
            data.openOrderId = idf.id;

        data.state = DealerState.Waiting;
        if (onOpen != null) {
            onOpen.run();
            onOpen = null;
        }
    }

    protected boolean cancel() {
        switch (data.state) {
            case Waiting:
                doCancel();
                return true;
            case Opening:
                log.warn("Cancel while opening");
                onOpen = () -> {
                    doCancel();
                    onOpen = null;
                };
                return true;
            case Cancelling:
            case NotProfitable:
                log.info("Cancel while in state: {}", data.state);
                return false;
            default:
                throw new IllegalStateException(data.state.name());
        }
    }

    private void doCancel() {
        assert data.state == DealerState.Waiting;
        log.info("Cancel @{} {} {}", data.getOpenExchange(), data.openOrderId, data.openedOrder);
        data.state = DealerState.Cancelling;
        data.getOpenExchange().getTrackingTradeService().cancelOrder(data.openOrderId);
    }

    public void handleCanceled(Future<Boolean> future) {
        assert data.state == DealerState.Cancelling;
        try {
            if (!future.get()) {
                log.warn("No such order {}", data.openOrderId);
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted!", e);
        } catch (ExecutionException | RuntimeException e) {
            log.warn("Error from {}", data.getOpenExchange(), e.getCause());
        }

        data.state = DealerState.NotProfitable;
        data.openExchange.getOrderTracker().removeTradeListener(listener);
        if (onCancel != null) {
            onCancel.run();
            onCancel = null;
        }
    }

    public LimitOrder deal(BigDecimal limitPrice) {

        //after my open order is closed at the open exchange, this money should disappear
        String openOutgoingCur = config.outgoingCurrency();

        String closeOutCur = config.incomingCurrency();

        Map<String, BigDecimal> openWallet = data.getOpenExchange().getWalletMonitor().getWallet();
        Map<String, BigDecimal> closeWallet = data.getCloseExchange().getWalletMonitor().getWallet();

        BigDecimal openOutGross = openWallet.get(openOutgoingCur);
        BigDecimal closeOutGross = closeWallet.get(closeOutCur);

        if (config.is(BID))
            openOutGross = openOutGross.divide(limitPrice, openWallet.get(closeOutCur).scale(), HALF_EVEN);
        else
            closeOutGross = closeOutGross.divide(limitPrice, closeWallet.get(openOutgoingCur).scale(), HALF_EVEN);
        // now both wallets should be expressed in base currency

        MarketMetaData openMetadata = data.getOpenExchange().getMarketMetadata(config.listing);
        MarketMetaData closeMetadata = data.getCloseExchange().getMarketMetadata(config.listing);

        // adjust amounts by dividing by two and check if it's not below minima
        {
            BigDecimal openOutHalf = openOutGross.divide(TWO, FLOOR);
            BigDecimal closeOutHalf = closeOutGross.divide(TWO, FLOOR);

            BigDecimal openLowLimit = openMetadata.getMinimumAmount();
            BigDecimal closeLowLimit = closeMetadata.getMinimumAmount();

            // if half of the wallet is greater than minimum, open for half, if less, open for full amount but only if it's ASK (arbitrary, avoid wallet collision)
            if (lt(openOutHalf, openLowLimit) || lt(closeOutHalf, closeLowLimit)) {
                if (config.is(skipSide)) {
                    log.info("Skip {} to avoid wallet collision over BID/ASK", skipSide);
                    return null;
                }
            } else {
                openOutGross = openOutHalf;
                closeOutGross = closeOutHalf;
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

        BigDecimal closableAmount = data.getCloseAmount(config.side, openOutGross, limitPrice);

        log.debug("open: {}", openOutGross);
        log.debug("close: {}", closeOutGross);
        log.debug("available: {}", closableAmount);

        BigDecimal openAmountActual = Ordering.natural().min(openOutGross, closableAmount, closeOutGross).setScale(DealerHelper.getScale(openMetadata, closeMetadata), FLOOR);

        if (!DealerHelper.checkMinima(openAmountActual, openMetadata)) {
            log.debug("Amount {} less than minimum", openAmountActual);
            return null;
        }

        openAmountActual = orderStrategy.getAmount(openAmountActual, closeMetadata.getMinimumAmount());
        return new LimitOrder.Builder(config.side, config.listing).limitPrice(limitPrice).tradableAmount(openAmountActual).build();
    }

    public void orderPlacementFailed(LimitOrder order){
        orderCloseStrategy.orderPlacementFailed(order);
    }

    public void orderPlacementFailed(MarketOrder order){
        orderCloseStrategy.orderPlacementFailed(order);
    }


    private void assertConfig(OrderType side, CurrencyPair listing) {
        if (config.side != side)
            throw new IllegalArgumentException(side.name());
        if (!config.listing.equals(listing))
            throw new IllegalArgumentException(listing.toString());
    }


    private boolean checkConfig(CurrencyPair currencyPair) {
        return config.listing.equals(currencyPair);
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
