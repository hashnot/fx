package com.hashnot.fx.strategy.pair;

import com.hashnot.xchange.ext.util.Numbers;
import com.xeiam.xchange.dto.Order;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigDecimal;
import java.util.Arrays;

import static com.hashnot.xchange.ext.util.Numbers.BigDecimal.TWO;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class GaussOrderOpenStrategyTest {
    static SimpleOrderOpenStrategy o = new GaussOrderOpenStrategy();

    private Order.OrderType side;

    public GaussOrderOpenStrategyTest(Order.OrderType side) {
        this.side = side;
    }

    @Parameterized.Parameters
    public static Iterable<Object[]> getParameters() {
        return Arrays.asList(
                new Object[]{ASK},
                new Object[]{BID}
        );
    }

    @Test
    public void testAmountRound() {
        assertEquals(ONE, o.getAmount(new BigDecimal("1.1"), ZERO));
    }

    @Test
    public void testAmountOne() {
        assertEquals(ONE, o.getAmount(ONE, ONE));
    }

    @Test
    public void testAmountZero() {
        assertEquals(ZERO, o.getAmount(ZERO, ONE));
    }

    @Test
    public void testGetPrice() throws Exception {
        // any scale >0
        BigDecimal input = TWO;
        BigDecimal lo = input;
        BigDecimal hi = input;
        if (side == ASK) {
            lo = lo.subtract(ONE);
        } else
            hi = hi.add(ONE);


        assertThat(o.getPrice(input, ONE, 1, side), new BigDecimalBetween(lo, hi));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTooSmallScale() {
        o.getPrice(ZERO, new BigDecimal(".5"), 0, side);
    }

    private static class BigDecimalBetween extends TypeSafeMatcher<BigDecimal> {
        private BigDecimal low;
        private BigDecimal hi;

        private BigDecimalBetween(BigDecimal low, BigDecimal hi) {
            this.low = low;
            this.hi = hi;
        }

        @Override
        protected boolean matchesSafely(BigDecimal item) {
            return Numbers.gt(item, low) && Numbers.lt(item, hi);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("between ").appendValue(low).appendText(" and ").appendValue(hi);
        }
    }
}