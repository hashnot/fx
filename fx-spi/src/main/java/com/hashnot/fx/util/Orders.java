package com.hashnot.fx.util;

import com.hashnot.fx.spi.ext.IFeeService;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * @author Rafał Krupiński
 */
public class Orders {
    final private static Logger log = LoggerFactory.getLogger(Orders.class);
    public static final BigDecimal _ONE = BigDecimal.ONE.negate();
    public static MathContext c = new MathContext(8, RoundingMode.HALF_UP);

    public static boolean equals(LimitOrder o1, LimitOrder o2) {
        return o1.getCurrencyPair().equals(o2.getCurrencyPair()) && o1.getType() == o2.getType()
                && equals(o1.getLimitPrice(), o2.getLimitPrice()) && equals(o1.getTradableAmount(), o2.getTradableAmount());
    }

    private static boolean equals(BigDecimal o1, BigDecimal o2) {
        return Numbers.equals(o1, o2);
    }

    public static CurrencyPair revert(CurrencyPair cp) {
        return new CurrencyPair(cp.counterSymbol, cp.baseSymbol);
    }

    public static int factor(Order.OrderType type) {
        return type == Order.OrderType.ASK ? 1 : -1;
    }

    public static BigDecimal bigFactor(Order.OrderType type) {
        return type == Order.OrderType.ASK ? BigDecimal.ONE : _ONE;
    }

    public static LimitOrder revert(LimitOrder order) {
        Order.OrderType type = revert(order.getType());
        BigDecimal orderPrice = order.getLimitPrice();
        BigDecimal price = BigDecimal.ONE.divide(orderPrice, c);
        BigDecimal amount = order.getTradableAmount().divide(orderPrice, c).stripTrailingZeros();
        return new LimitOrder(type, amount, revert(order.getCurrencyPair()), null, null, price);
    }

    public static Order.OrderType revert(Order.OrderType type) {
        return type == Order.OrderType.ASK ? Order.OrderType.BID : Order.OrderType.ASK;
    }

    public static LimitOrder closing(LimitOrder order) {
        return new LimitOrder(revert(order.getType()), order.getTradableAmount(), order.getCurrencyPair(), null, null, order.getLimitPrice());
    }

    public static LimitOrder closing(LimitOrder order, IFeeService feeService) {
        LimitOrder result = closing(order);
            result.setNetPrice(getNetPrice(result, FeeHelper.getFeePercent(feeService, result)));
        return result;
    }

    public static BigDecimal getNetPrice(LimitOrder order, BigDecimal feePercent) {
        return getNetPrice(order.getLimitPrice(), order.getType(), feePercent);
    }

    public static BigDecimal getNetPrice(BigDecimal limitPrice, Order.OrderType type, BigDecimal feePercent) {
        return limitPrice.multiply(BigDecimal.ONE.add(feePercent), c).stripTrailingZeros();
    }

    public static String incomingCurrency(Order order) {
        CurrencyPair pair = order.getCurrencyPair();
        return order.getType() == Order.OrderType.ASK ? pair.counterSymbol : pair.baseSymbol;
    }

    public static String outgoingCurrency(Order order) {
        CurrencyPair pair = order.getCurrencyPair();
        return order.getType() == Order.OrderType.ASK ? pair.baseSymbol : pair.counterSymbol;
    }

    // TODO take fee policy into account
    public static BigDecimal incomingAmount(LimitOrder order) {
        if (order.getType() == Order.OrderType.ASK) {
            BigDecimal result = order.getTradableAmount().multiply(order.getNetPrice(), c).stripTrailingZeros();
            if (result.scale() > c.getPrecision())
                return result.setScale(c.getPrecision(), c.getRoundingMode());
            return result;
        } else {
            return order.getTradableAmount();
        }
    }

    public static BigDecimal outgoingAmount(LimitOrder order) {
        if (order.getType() == Order.OrderType.ASK) {
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
}
