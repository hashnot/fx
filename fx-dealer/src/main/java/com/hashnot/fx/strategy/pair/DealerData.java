package com.hashnot.fx.strategy.pair;

import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hashnot.xchange.ext.util.Comparables.lt;
import static com.hashnot.xchange.ext.util.Orders.c;
import static com.hashnot.xchange.ext.util.Prices.isFurther;
import static java.math.BigDecimal.ZERO;

/**
 * Structure holding state of the dealer state machine
 *
 * @author Rafał Krupiński
 */
public class DealerData {
    final private static Logger log = LoggerFactory.getLogger(DealerData.class);

    // side -> map(exchange -> best offer price)
    final private Map<Exchange, BigDecimal> bestOffers = Collections.checkedMap(new HashMap<>(), Exchange.class, BigDecimal.class);

    private IExchangeMonitor closeExchange;

    public IExchangeMonitor openExchange;

    public LimitOrder openedOrder;

    public String openOrderId;
    public List<LimitOrder> closingOrders;

    public DealerState state = DealerState.NotProfitable;

    /**
     * Call after open closing orders or order has changed
     *
     * @return true if my open order would be still profitable after it's filled and I fill the 'closing' orders.
     */
    public boolean checkProfitable() {
        assert openedOrder != null;
        assert closingOrders != null;

        // return if there is open and profitable order
        BigDecimal closeAmount = getCloseAmount(openedOrder.getType(), openedOrder.getCurrencyPair(), openedOrder.getLimitPrice());
        log.debug("Closable for {}: {} (opened: {})", openedOrder.getLimitPrice(), closeAmount, openedOrder.getTradableAmount());
        return !lt(closeAmount, openedOrder.getTradableAmount());
    }

    private BigDecimal getCloseAmount(Order.OrderType side, CurrencyPair listing, BigDecimal limitPrice) {
        String closeOutCur = DealerHelper.incomingCurrency(side, listing);

        BigDecimal closeOutGross = closeExchange.getWalletMonitor().getWallet(closeOutCur);
        BigDecimal closeOutNet = DealerHelper.applyFeeToWallet(closeOutGross, closeExchange.getMarketMetadata(listing));

        return getCloseAmount(side, closeOutNet, limitPrice);
    }

    public BigDecimal getCloseAmount(Order.OrderType side, BigDecimal amountLimit, BigDecimal limitPrice) {
        if (side == Order.OrderType.ASK)
            return totalAmountByValue(amountLimit, limitPrice);
        else
            return totalAmountByAmount(amountLimit, limitPrice);
    }

    public BigDecimal totalAmountByAmount(BigDecimal amountLimit, BigDecimal netPriceLimit) {
        BigDecimal totalAmount = ZERO;
        Order.OrderType type = Order.OrderType.ASK;
        for (LimitOrder order : closingOrders) {
            assert type != order.getType();

            log.debug("order {}", order);

            if (isFurther(order.getLimitPrice(), netPriceLimit, order.getType()))
                break;

            totalAmount = totalAmount.add(order.getTradableAmount());

            if (!lt(totalAmount, amountLimit)) {
                break;
            }
        }
        return totalAmount;
    }

    public BigDecimal totalAmountByValue(BigDecimal valueLimit, BigDecimal priceLimit) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        Order.OrderType type = Order.OrderType.BID;
        for (LimitOrder order : closingOrders) {
            assert type != order.getType();

            BigDecimal netPrice = Orders.getNetPrice(order.getLimitPrice(), type, closeExchange.getMarketMetadata(order.getCurrencyPair()).getOrderFeeFactor());
            log.debug("order {} net {}", order, netPrice);

            if (isFurther(order.getLimitPrice(), priceLimit, order.getType()))
                break;
            BigDecimal curAmount = order.getTradableAmount();
            BigDecimal curValue = curAmount.multiply(order.getLimitPrice(), c);
            totalValue = totalValue.add(curValue);

            curAmount = curValue.divide(order.getLimitPrice(), c);
            totalAmount = totalAmount.add(curAmount);

            if (!lt(totalValue, valueLimit)) {
                break;
            }
        }
        return totalAmount;
    }

    public DealerData() {
    }

    public DealerData(IExchangeMonitor openExchange, IExchangeMonitor closeExchange, Map<Exchange, BigDecimal> bestOffers) {
        this.closeExchange = closeExchange;
        this.openExchange = openExchange;
        this.bestOffers.putAll(bestOffers);
    }

    public Map<Exchange, BigDecimal> getBestOffers() {
        return bestOffers;
    }

    public IExchangeMonitor getCloseExchange() {
        return closeExchange;
    }

    public void setCloseExchange(IExchangeMonitor closeExchange) {
        this.closeExchange = closeExchange;
    }

    public IExchangeMonitor getOpenExchange() {
        return openExchange;
    }

    public void setOpenExchange(IExchangeMonitor openExchange) {
        this.openExchange = openExchange;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof DealerData && equals((DealerData) o);
    }

    protected boolean equals(DealerData that) {
        return bestOffers.equals(that.bestOffers)
                && (closeExchange == null ? that.closeExchange == null : closeExchange.equals(that.closeExchange))
                && (openExchange == null ? that.openExchange == null : openExchange.equals(that.openExchange))
                && (closingOrders == null ? that.closingOrders == null : closingOrders.equals(that.closingOrders))
                && (openOrderId != null ? openOrderId.equals(that.openOrderId) : that.openOrderId == null)
                && (openedOrder != null ? openedOrder.equals(that.openedOrder) : that.openedOrder == null)
                && state == that.state;

    }

    @Override
    public int hashCode() {
        int result = bestOffers.hashCode();
        result = 31 * result + (closeExchange != null ? closeExchange.hashCode() : 0);
        result = 31 * result + (openExchange != null ? openExchange.hashCode() : 0);
        result = 31 * result + (closingOrders != null ? closingOrders.hashCode() : 0);
        result = 31 * result + (openOrderId != null ? openOrderId.hashCode() : 0);
        result = 31 * result + (openedOrder != null ? openedOrder.hashCode() : 0);
        result = 31 * result + state.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "DealerData{" +
                "bestOffers=" + bestOffers +
                ", closeExchange=" + closeExchange +
                ", openExchange=" + openExchange +
                ", openedOrder=" + openedOrder +
                ", openOrderId='" + openOrderId + '\'' +
                ", closingOrders=" + closingOrders +
                ", state=" + state +
                '}';
    }

    /**
     * Helper method
     */
    @Nullable
    public BigDecimal getOpenPrice() {
        return openedOrder == null ? null : openedOrder.getLimitPrice();
    }
}
