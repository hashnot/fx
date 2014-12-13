package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.*;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;

import static com.hashnot.xchange.ext.util.Numbers.Price.*;
import static com.hashnot.xchange.ext.util.Numbers.eq;
import static com.hashnot.xchange.ext.util.Numbers.lt;
import static com.hashnot.xchange.ext.util.Orders.c;
import static com.hashnot.xchange.ext.util.Orders.revert;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;

/**
 * @author Rafał Krupiński
 */
public class Dealer implements IBestOfferListener {
    final private static Logger log = LoggerFactory.getLogger(Dealer.class);

    final private PairTradeListener orderManager;
    final private IOrderBookSideMonitor orderBookSideMonitor;
    final private IOrderBookSideListener orderBookSideListener;

    final private Map<Exchange, IExchangeMonitor> monitors;

    final private DealerData data;

    final private DealerConfig config;

    /**
     * If the new price is the best, update internal state and change order book monitor to monitor new best market
     */
    @Override
    synchronized public void updateBestOffer(BestOfferEvent evt) {
        assert config.side == evt.source.side;
        assert config.listing.equals(evt.source.market.listing);

        Exchange eventExchange = evt.source.market.exchange;
        if (!hasMinimumMoney(eventExchange, evt.price)) {
            log.debug("Ignore exchange {} without enough money", evt.source);
            return;
        }

        BigDecimal newPrice = evt.price;

        BigDecimal oldPrice = getBestOffers().get(eventExchange);

        // don't update the order if the new 3rd party order is not better
        if (oldPrice != null && eq(newPrice, oldPrice)) {
            log.warn("Price didn't change");
            return;
        }

        log.info("Best offer {} @{}/{}; mine {}@{}", newPrice, config.side, eventExchange, data.getOpenPrice(), getOpenExchange());

        boolean mineTop = isMineTop(newPrice);
        log.debug("Mine on top: {}", mineTop);
        if (mineTop) return;

        getBestOffers().put(eventExchange, newPrice);

        // it's important to first update open before close exchange,
        // because we register ourselves as a listener for close order book if close != open

        boolean dirty = false;
        if (!eventExchange.equals(getOpenExchange())) {
            BigDecimal oldOpenPrice = forNull(revert(config.side));
            if (getOpenExchange() != null)
                oldOpenPrice = getBestOffers().getOrDefault(getOpenExchange(), oldOpenPrice);

            // worse is better, because we open between closer and further on the further exchange
            if (isFurther(newPrice, oldOpenPrice, config.side)) {
                data.setOpenExchange(eventExchange);
                dirty = true;
            }
        }

        Exchange oldClose = getCloseExchange();

        if (!eventExchange.equals(getCloseExchange())) {
            BigDecimal closeMarketPrice = forNull(config.side);
            if (getCloseExchange() != null)
                closeMarketPrice = getBestOffers().getOrDefault(getCloseExchange(), closeMarketPrice);

            if (isCloser(newPrice, closeMarketPrice, config.side)) {
                data.setCloseExchange(eventExchange);
                dirty = true;
            }
        }

        if (dirty) {
            updateCloseMarket(oldClose);
            if (orderManager.isActive())
                orderManager.cancel();
        }
    }

    private void updateCloseMarket(Exchange oldExchange) {
        log.debug("open {} old close {} new close {}", getOpenExchange(), oldExchange, getCloseExchange());
        if (oldExchange != null && oldExchange != getCloseExchange()) {
            MarketSide oldCloseSide = new MarketSide(oldExchange, config.listing, config.side);
            log.debug("Remove OBL from {}", oldCloseSide);
            orderBookSideMonitor.removeOrderBookSideListener(orderBookSideListener, oldCloseSide);
        }

        if (getCloseExchange() != getOpenExchange()) {
            MarketSide marketSide = new MarketSide(getCloseExchange(), config.listing, config.side);
            log.debug("Add OBL to {}", marketSide);
            orderBookSideMonitor.addOrderBookSideListener(orderBookSideListener, marketSide);
        }
    }

    protected boolean isMineTop(BigDecimal newTopPrice) {
        LimitOrder order = orderManager.getOpenOrder();
        return (order != null && !isFurther(order.getLimitPrice(), newTopPrice, config.side));
    }

    private boolean hasMinimumMoney(Exchange exchange, BigDecimal price) {
        IExchangeMonitor monitor = monitors.get(exchange);
        BigDecimal outAmount = monitor.getWalletMonitor().getWallet(Orders.outgoingCurrency(config.side, config.listing));
        BigDecimal amountMinimum = monitor.getMarketMetadata(config.listing).getAmountMinimum();

        BigDecimal baseAmount;
        if (config.side == ASK)
            baseAmount = outAmount;
        else {
            baseAmount = outAmount.divide(price, amountMinimum.scale(), c.getRoundingMode());
        }

        return !lt(baseAmount, amountMinimum);
    }

    public DealerConfig getConfig() {
        return config;
    }

    private Map<Exchange, BigDecimal> getBestOffers() {
        return data.getBestOffers();
    }

    private Exchange getOpenExchange() {
        return data.getOpenExchange();
    }

    private Exchange getCloseExchange() {
        return data.getCloseExchange();
    }

    public Dealer(IOrderBookSideMonitor orderBookSideMonitor, Map<Exchange, IExchangeMonitor> monitors, DealerConfig config, SimpleOrderOpenStrategy orderStrategy, SimpleOrderCloseStrategy orderCloseStrategy) {
        this(orderBookSideMonitor, monitors, config, orderStrategy, orderCloseStrategy, new DealerData());
    }

    protected Dealer(IOrderBookSideMonitor orderBookSideMonitor, Map<Exchange, IExchangeMonitor> monitors, DealerConfig config, SimpleOrderOpenStrategy orderStrategy, SimpleOrderCloseStrategy orderCloseStrategy, DealerData data) {
        this(orderBookSideMonitor, new PairTradeListener(orderCloseStrategy, monitors, data), monitors, config, orderStrategy, data);
    }

    protected Dealer(IOrderBookSideMonitor orderBookSideMonitor, PairTradeListener orderManager, Map<Exchange, IExchangeMonitor> monitors, DealerConfig config, SimpleOrderOpenStrategy orderStrategy, DealerData data) {
        this(orderBookSideMonitor, orderManager, monitors, config, new PairOrderBookSideListener(config, data, orderManager, orderStrategy, monitors), data);
    }

    public Dealer(IOrderBookSideMonitor orderBookSideMonitor, PairTradeListener orderManager, Map<Exchange, IExchangeMonitor> monitors, DealerConfig config, IOrderBookSideListener orderBookSideListener, DealerData data) {
        this.orderBookSideMonitor = orderBookSideMonitor;
        this.monitors = monitors;
        this.config = config;
        this.data = data;
        this.orderManager = orderManager;
        this.orderBookSideListener = orderBookSideListener;
    }

}
