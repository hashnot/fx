package com.hashnot.fx.strategy.pair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

import static com.hashnot.xchange.ext.util.Numbers.eq;
import static com.hashnot.xchange.ext.util.Numbers.lt;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class GaussOrderOpenStrategy extends SimpleOrderOpenStrategy {
    private Random random = new Random(1181783497276652981L ^ System.currentTimeMillis());

    /**
     * {@link java.util.Random#nextGaussian()} has a standard deviation of 1, which makes the p(result<1)=31%
     * Tune the gauss
     */
    static private final int stdDevFactor = 5;
    static final private RoundingMode round = RoundingMode.HALF_EVEN;

    @Override
    public BigDecimal getAmount(BigDecimal amount) {
        return amount.round(AMOUNT_MCTX);
    }

    @Override
    protected BigDecimal getDelta(BigDecimal low, BigDecimal diff, int scale) {
        BigDecimal add = getRandom(scale).multiply(diff).setScale(scale, round);
        if (eq(add, ZERO)) {
            add = step(scale);
            if (!lt(add, diff))
                throw new IllegalArgumentException("Cannot find price between (" + low + ", add(" + diff + ") ) with scale " + scale);
        } else if (!lt(add, diff)) {
            add = diff.subtract(step(scale));
        }
        return add;
    }

    protected BigDecimal getRandom(int scale) {
        return BigDecimal.valueOf((Math.abs(random.nextGaussian()) / stdDevFactor)).setScale(scale, round);
    }
}
