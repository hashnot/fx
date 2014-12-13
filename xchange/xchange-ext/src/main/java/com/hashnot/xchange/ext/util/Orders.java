package com.hashnot.xchange.ext.util;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static com.hashnot.xchange.ext.util.Comparables.eq;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;

/**
 * Util class for orders
 *
 * @author Rafał Krupiński
 */
public class Orders {
    final private static Logger log = LoggerFactory.getLogger(Orders.class);
    public static MathContext c = new MathContext(16, RoundingMode.HALF_UP);

    public static boolean equals(LimitOrder o1, LimitOrder o2) {
        return o1.getCurrencyPair().equals(o2.getCurrencyPair()) && o1.getType() == o2.getType()
                && eq(o1.getLimitPrice(), o2.getLimitPrice()) && eq(o1.getTradableAmount(), o2.getTradableAmount());
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
        return LimitOrder.Builder.from(order).orderType(revert(order.getType())).build();
    }

    public static BigDecimal getNetPrice(LimitOrder order, BigDecimal feePercent) {
        return getNetPrice(order.getLimitPrice(), order.getType(), feePercent);
    }

    public static BigDecimal getNetPrice(BigDecimal limitPrice, Order.OrderType type, BigDecimal feePercent) {
        BigDecimal feeFactor = (type == BID) ? feePercent : feePercent.negate();
        return applyFee(limitPrice, feeFactor);
    }

    public static BigDecimal applyFee(BigDecimal amount, BigDecimal feeFactor) {
        return amount.multiply(ONE.add(feeFactor));
    }

    public static String incomingCurrency(Order order) {
        CurrencyPair pair = order.getCurrencyPair();
        return order.getType() == ASK ? pair.counterSymbol : pair.baseSymbol;
    }

    public static String outgoingCurrency(Order order) {
        return outgoingCurrency(order.getType(), order.getCurrencyPair());
    }

    /**
     * @param type override order type
     */
    public static String outgoingCurrency(Order.OrderType type, CurrencyPair pair) {
        return type == ASK ? pair.baseSymbol : pair.counterSymbol;
    }

    public static BigDecimal value(LimitOrder order) {
        return order.getTradableAmount().multiply(order.getLimitPrice(), c);
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
