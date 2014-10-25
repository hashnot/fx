package com.hashnot.fx.util;

import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static com.hashnot.fx.util.Numbers.isEqual;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;

/**
 * @author Rafał Krupiński
 */
public class Orders {
    final private static Logger log = LoggerFactory.getLogger(Orders.class);
    public static MathContext c = new MathContext(16, RoundingMode.HALF_UP);

    public static boolean equals(LimitOrder o1, LimitOrder o2) {
        return o1.getCurrencyPair().equals(o2.getCurrencyPair()) && o1.getType() == o2.getType()
                && isEqual(o1.getLimitPrice(), o2.getLimitPrice()) && isEqual(o1.getTradableAmount(), o2.getTradableAmount());
    }

    public static CurrencyPair revert(CurrencyPair cp) {
        return new CurrencyPair(cp.counterSymbol, cp.baseSymbol);
    }

    public static int factor(Order.OrderType type) {
        return type == ASK ? 1 : -1;
    }

    public static Order.OrderType revert(Order.OrderType type) {
        return type == ASK ? BID : ASK;
    }

    public static LimitOrder closing(LimitOrder order) {
        return new LimitOrder(revert(order.getType()), order.getTradableAmount(), order.getCurrencyPair(), null, null, order.getLimitPrice());
    }

    public static LimitOrder closing(LimitOrder order, IExchange feeService) {
        LimitOrder result = closing(order);
        updateNetPrice(result, feeService);
        return result;
    }

    public static void updateNetPrice(LimitOrder order, IExchange x) {
        order.setNetPrice(getNetPrice(order, x.getMarketMetadata(order.getCurrencyPair()).getOrderFeeFactor()));
    }

    public static BigDecimal getNetPrice(LimitOrder order, BigDecimal feePercent) {
        return getNetPrice(order.getLimitPrice(), order.getType(), feePercent);
    }

    public static BigDecimal getNetPrice(BigDecimal limitPrice, Order.OrderType type, BigDecimal feePercent) {
        BigDecimal feeFactor = (type == BID) ? feePercent : feePercent.negate();
        return applyFee(limitPrice, feeFactor);
    }

    public static BigDecimal applyFee(BigDecimal amount, BigDecimal feeFactor){
        return amount.multiply(ONE.add(feeFactor));
    }

    public static String incomingCurrency(Order order) {
        CurrencyPair pair = order.getCurrencyPair();
        return order.getType() == ASK ? pair.counterSymbol : pair.baseSymbol;
    }

    public static String outgoingCurrency(Order order) {
        return outgoingCurrency(order, order.getType());
    }

    /**
     * @param type override order type
     */
    public static String outgoingCurrency(Order order, Order.OrderType type) {
        CurrencyPair pair = order.getCurrencyPair();
        return type == ASK ? pair.baseSymbol : pair.counterSymbol;
    }

    // TODO take fee policy into account
    public static BigDecimal incomingAmount(LimitOrder order) {
        if (order.getType() == ASK) {
            BigDecimal result = order.getTradableAmount().multiply(order.getNetPrice(), c).stripTrailingZeros();
            if (result.scale() > c.getPrecision())
                return result.setScale(c.getPrecision(), c.getRoundingMode());
            return result;
        } else {
            return order.getTradableAmount();
        }
    }

    public static BigDecimal outgoingAmount(LimitOrder order) {
        if (order.getType() == ASK) {
            return order.getTradableAmount();
        } else {
            BigDecimal result = order.getTradableAmount().multiply(order.getLimitPrice(), c).stripTrailingZeros();
            if (result.scale() > c.getPrecision())
                return result.setScale(c.getPrecision(), c.getRoundingMode());
            return result;
        }
    }

    public static boolean isProfitable(LimitOrder o1, LimitOrder o2) {
        boolean result = o1.getNetPrice().compareTo(o2.getNetPrice()) * factor(o1.getType()) >= 0;
        log.debug("{} {} {} <=> {} {} {} {}profitable", o1.getType(), o1.getLimitPrice(), o1.getNetPrice(), o2.getNetPrice(), o2.getLimitPrice(), o2.getType(), result ? "" : "not ");
        return result;
    }

    public static BigDecimal value(LimitOrder order) {
        return order.getTradableAmount().multiply(order.getLimitPrice(), c);
    }

    public static BigDecimal netValue(LimitOrder order) {
        BigDecimal netPrice = order.getNetPrice();
        if (netPrice == null)
            throw new IllegalArgumentException("Null net price");
        return order.getTradableAmount().multiply(netPrice, c).stripTrailingZeros();
    }

    public static int compareToLimitPrice(LimitOrder order, BigDecimal limitPrice) {
        return order.getLimitPrice().compareTo(limitPrice) * factor(order.getType());
    }

    public static int compareToNetPrice(LimitOrder order, BigDecimal limitPrice) {
        return order.getNetPrice().compareTo(limitPrice) * factor(order.getType());
    }

    public static void validatePair(LimitOrder o1, LimitOrder o2) {
        if (!o1.getCurrencyPair().equals(o2.getCurrencyPair()))
            throw new IllegalArgumentException("Invalid CurrencyPair");
    }

    public static void validateSameDirection(LimitOrder o1, LimitOrder o2) {
        if (o1.getType() != o2.getType())
            throw new IllegalArgumentException("Orders in different direction");
    }

    public static LimitOrder add(LimitOrder o1, LimitOrder o2) {
        if (o1.getType() != o2.getType() || o1.getLimitPrice().compareTo(o2.getLimitPrice()) != 0)
            throw new IllegalArgumentException("Incompatible LimitOrders");
        return new LimitOrder(o1.getType(), o1.getTradableAmount().add(o2.getTradableAmount()), o1.getCurrencyPair(), null, null, o1.getLimitPrice());
    }

    public static LimitOrder withAmount(LimitOrder o, BigDecimal tradableAmount) {
        return new LimitOrder(o.getType(), tradableAmount, o.getCurrencyPair(), o.getId(), o.getTimestamp(), o.getLimitPrice());
    }

    public static LimitOrder withPrice(LimitOrder o, BigDecimal limitPrice) {
        return new LimitOrder(o.getType(), o.getTradableAmount(), o.getCurrencyPair(), o.getId(), o.getTimestamp(), limitPrice);
    }

    public static BigDecimal betterPrice(BigDecimal price, BigDecimal delta, Order.OrderType type) {
        BigDecimal myDelta = type == ASK ? delta : delta.negate();
        return price.add(myDelta);
    }

    public static BigDecimal worsePrice(BigDecimal price, BigDecimal delta, Order.OrderType type) {
        return betterPrice(price, delta, type == ASK ? BID : ASK);
    }
}
