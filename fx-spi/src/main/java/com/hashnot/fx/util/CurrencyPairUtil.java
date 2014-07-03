package com.hashnot.fx.util;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

import static com.xeiam.xchange.currency.Currencies.*;

/**
 * @author Rafał Krupiński
 */
public class CurrencyPairUtil {
    public static final CurrencyPair EUR_PLN = new CurrencyPair(EUR, PLN);
    public static MathContext c = new MathContext(8, RoundingMode.HALF_UP);
    private static List<String> baseCurrencies = Arrays.asList(EUR, GBP, AUD, NZD, USD, CAD, CHF, JPY);

    public static boolean isNonReversed(CurrencyPair cp) {
        return isNonReversed(cp.baseSymbol, cp.counterSymbol);
    }

    public static boolean isNonReversed(String base, String counter) {
        assert !base.equals(counter) : "Base and counter are equal";
        int baseIdx = baseCurrencies.indexOf(base);
        if (baseIdx < 0) baseIdx = Integer.MAX_VALUE;

        int quotIDx = baseCurrencies.indexOf(counter);
        if (quotIDx < 0) quotIDx = Integer.MAX_VALUE;

        if (baseIdx == quotIDx)
            throw new IllegalArgumentException("Neither is a base currency");

        return baseIdx < quotIDx;

    }

    public static CurrencyPair reverse(CurrencyPair cp) {
        return new CurrencyPair(cp.counterSymbol, cp.baseSymbol);
    }

    public static List<LimitOrder> get(OrderBook orderBook, Order.OrderType dir) {
        return dir == Order.OrderType.ASK ? orderBook.getAsks() : orderBook.getBids();
    }

    public static int factor(Order.OrderType type) {
        return type == Order.OrderType.ASK ? 1 : -1;
    }

    public static final BigDecimal _ONE = BigDecimal.ONE.negate();

    public static BigDecimal bigFactor(Order.OrderType type) {
        return type == Order.OrderType.ASK ? BigDecimal.ONE : _ONE;
    }

    public static LimitOrder reverse(LimitOrder order) {
        Order.OrderType type = revert(order.getType());
        BigDecimal orderPrice = order.getLimitPrice();
        BigDecimal price = BigDecimal.ONE.divide(orderPrice, c);
        BigDecimal amount = order.getTradableAmount().divide(orderPrice, c);
        return new LimitOrder(type, amount, reverse(order.getCurrencyPair()), null, null, price);
    }

    public static Order.OrderType revert(Order.OrderType type) {
        return type == Order.OrderType.ASK ? Order.OrderType.BID : Order.OrderType.ASK;
    }

    public static LimitOrder closing(LimitOrder order) {
        return new LimitOrder(revert(order.getType()), order.getTradableAmount(), order.getCurrencyPair(), null, null, order.getLimitPrice());
    }
}
