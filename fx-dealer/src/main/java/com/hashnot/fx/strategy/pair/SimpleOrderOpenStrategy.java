package com.hashnot.fx.strategy.pair;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static com.hashnot.xchange.ext.util.BigDecimals.TWO;
import static com.hashnot.xchange.ext.util.Comparables.lt;
import static com.xeiam.xchange.dto.Order.OrderType.BID;

/**
 * @author Rafał Krupiński
 */
public class SimpleOrderOpenStrategy {
    protected MathContext AMOUNT_MCTX = new MathContext(1, RoundingMode.FLOOR);

    public LimitOrder createLimitOrder(Order.OrderType side, BigDecimal maxAmount, BigDecimal minAmount, CurrencyPair pair, BigDecimal priceLow, BigDecimal diff, int scale) {
        BigDecimal price = getPrice(priceLow, diff, scale, side);
        return new LimitOrder.Builder(side, pair).tradableAmount(getAmount(maxAmount, minAmount)).limitPrice(price).build();
    }

    /**
     * If amount is between minAmount and minAmount*2 return minAmount. Otherwise our order could be partially closed
     * and we would have no way to make matching closing order
     */
    public BigDecimal getAmount(BigDecimal amount, BigDecimal minAmount) {
        if (lt(amount, minAmount))
            return null;
        else if (lt(amount, minAmount.multiply(TWO)))
            return minAmount;
        else
            return amount.round(AMOUNT_MCTX);
    }

    public BigDecimal getPrice(BigDecimal low, BigDecimal diff, int scale, Order.OrderType side) {
        BigDecimal delta = getDelta(low, diff.abs(), scale);
        return low.add(adjust(delta, side));
    }

    private BigDecimal adjust(BigDecimal value, Order.OrderType side) {
        return side == BID ? value : value.negate();
    }

    protected BigDecimal getDelta(BigDecimal low, BigDecimal diff, int scale) {
        return step(scale);
    }

    protected BigDecimal step(int scale) {
        return BigDecimal.ONE.movePointLeft(scale);
    }
}
