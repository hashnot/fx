package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.IOrderBookSideListener;
import com.hashnot.fx.framework.OrderBookSideUpdateEvent;
import com.hashnot.fx.util.OrderBooks;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.hashnot.xchange.ext.util.Numbers.Price.isFurther;
import static com.hashnot.xchange.ext.util.Numbers.lt;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class PairOrderBookSideListener implements IOrderBookSideListener{
    final private static Logger log = LoggerFactory.getLogger(PairOrderBookSideListener.class);

    static private final BigDecimal TWO = new BigDecimal(2);

    final private Map<Exchange, IExchangeMonitor> monitors;

    final private DealerConfig config;
    final private DealerData data;
    final private PairTradeListener orderManager;
    final private SimpleOrderOpenStrategy orderStrategy;

    public PairOrderBookSideListener(DealerConfig config, DealerData data, PairTradeListener orderManager, SimpleOrderOpenStrategy orderStrategy, Map<Exchange, IExchangeMonitor> monitors) {
        this.orderStrategy = orderStrategy;
        this.monitors = monitors;
        this.config = config;
        this.data = data;
        this.orderManager = orderManager;
    }

    /**
     * Called when order book changes on the close exchange
     */
    @Override
    public void orderBookSideChanged(OrderBookSideUpdateEvent evt) {
        assert config.side == evt.source.side;
        assert config.listing.equals(evt.source.market.listing);

        // enable when we have at open!=close exchanges before listening to order books
        // TODO initial state assert !getCloseExchange().equals(getOpenExchange()) : "Didn't find 2 exchanges with sufficient funds " + side;
        if (data.getCloseExchange().equals(data.getOpenExchange())) {
            log.info("Didn't find 2 exchanges with sufficient funds {}", config.side);
            return;
        }

        log.info("order book from {}", evt.source);

        if (!evt.source.market.exchange.equals(data.getCloseExchange())) {
            log.warn("Unexpected OrderBook update from {}", evt.source);
            return;
        }

        //TODO change may mean that we need to change the open order to smaller one

        // open or update order
        IExchangeMonitor closeMonitor = monitors.get(data.getCloseExchange());
        List<LimitOrder> limited = OrderBooks.removeOverLimit(evt.newOrders, getLimit(closeMonitor, config.listing.baseSymbol), getLimit(closeMonitor, config.listing.counterSymbol));

        // TODO remove net prices
        Orders.updateNetPrices(limited, closeMonitor.getMarketMetadata(config.listing).getOrderFeeFactor());

        if (orderManager.isActive()) {
            orderManager.update(evt.newOrders);
            // return if there is open and profitable order
            LimitOrder openOrder = orderManager.getOpenOrder();
            if (openOrderStillProfitable(openOrder, evt)) {
                log.debug("Order still profitable");
                return;
            } else {
                log.info("Order unprofitable {}", openOrder);
                orderManager.cancel();
            }
        }

        LimitOrder closeOrder = Orders.closing(evt.newOrders.get(0), closeMonitor);

        BigDecimal openGrossPrice = data.getBestOffers().get(data.getOpenExchange());
        BigDecimal openNetPrice = getOpenNetPrice(openGrossPrice);
        BigDecimal diff = openNetPrice.subtract(closeOrder.getNetPrice());

        // ask -> diff > 0
        boolean profitable = isFurther(diff, ZERO, config.side);

        IExchangeMonitor openMonitor = monitors.get(data.getOpenExchange());
        if (profitable) {
            int scale = openMonitor.getMarketMetadata(config.listing).getPriceScale();
            // TODO use getAmount
            openGrossPrice = orderStrategy.getPrice(openGrossPrice, diff, scale, config.side);
            openNetPrice = getOpenNetPrice(openGrossPrice);
        }
        LimitOrder openOrder = new LimitOrder.Builder(config.side, config.listing).limitPrice(openGrossPrice).build();
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

    private boolean openOrderStillProfitable(LimitOrder openOrder, OrderBookSideUpdateEvent evt) {
        // working on a limited OrderBooks,
        if (evt.getChanges().isEmpty())
            return true;
        BigDecimal closeAmount = DealerHelper.getCloseAmount(evt.newOrders, openOrder, monitors.get(data.getCloseExchange()));
        return !lt(closeAmount, openOrder.getTradableAmount());
    }

    private BigDecimal getOpenNetPrice(BigDecimal openGrossPrice) {
        return Orders.getNetPrice(openGrossPrice, config.side, monitors.get(data.getOpenExchange()).getMarketMetadata(config.listing).getOrderFeeFactor());
    }

    protected static BigDecimal getLimit(IExchangeMonitor monitor, String currency) {
        return monitor.getWalletMonitor().getWallet(currency).multiply(TWO);
    }

}