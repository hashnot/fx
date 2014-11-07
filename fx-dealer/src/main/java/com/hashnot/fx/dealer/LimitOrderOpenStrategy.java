package com.hashnot.fx.dealer;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Random;

import static com.hashnot.xchange.ext.util.Numbers.eq;
import static com.hashnot.xchange.ext.util.Numbers.lt;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class LimitOrderOpenStrategy {
    private Random random = new Random(1181783497276652981L ^ System.currentTimeMillis());
    protected MathContext AMOUNT_MCTX = new MathContext(1, RoundingMode.FLOOR);

    /**
     * {@link java.util.Random#nextGaussian()} has a standard deviation of 1, which makes the p(result<5)=99.999%.
     * Make it p(result<1) by dividing by 5
     */
    static private final int stdDevFactor = 50;

    public LimitOrder createLimitOrder(Order.OrderType type, BigDecimal maxAmount, CurrencyPair pair, BigDecimal priceLow, BigDecimal diff, int scale) {
        BigDecimal price = getPrice(priceLow, diff, scale);
        return new LimitOrder.Builder(type, pair).tradableAmount(getAmount(maxAmount)).limitPrice(price).build();
    }

    protected BigDecimal getAmount(BigDecimal amount) {
        return amount.round(AMOUNT_MCTX);
    }

    protected BigDecimal getPrice(BigDecimal low, BigDecimal diff, int scale) {
        BigDecimal add = BigDecimal.valueOf((Math.abs(random.nextGaussian()) / stdDevFactor)).multiply(diff).setScale(scale, RoundingMode.HALF_EVEN);
        if (eq(add, ZERO)) {
            add = BigDecimal.ONE.movePointLeft(scale);
            if (!lt(add, diff))
                throw new IllegalArgumentException("Cannot find price between (" + low + ", +" + diff + ") with scale " + scale);
        } else if (!lt(add, diff)) {
            add = diff.subtract(BigDecimal.ONE.movePointLeft(scale));
        }

        return low.add(add);
    }
}
