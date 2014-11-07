package com.hashnot.fx.dealer;

import com.hashnot.xchange.ext.util.Numbers;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;

import java.math.BigDecimal;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class LimitOrderOpenStrategyTest {
    static LimitOrderOpenStrategy o = new LimitOrderOpenStrategy();

    public static void main(String[] args) {
        for (int i = 0; i < 100; i++) {
            System.out.println(o.getPrice(ZERO, ONE, 5));
        }
    }

    @Test
    public void testAmountRound() {
        assertEquals(ONE, o.getAmount(new BigDecimal("1.1")));
    }

    @Test
    public void testAmountOne() {
        assertEquals(ONE, o.getAmount(ONE));
    }

    @Test
    public void testAmountZero() {
        assertEquals(ZERO, o.getAmount(ZERO));
    }

    @Test
    public void testGetPrice() throws Exception {
        // any scale >0
        assertThat(o.getPrice(ZERO, ONE, 1), new TypeSafeMatcher<BigDecimal>() {
            @Override
            protected boolean matchesSafely(BigDecimal item) {
                return Numbers.gt(item, ZERO) && Numbers.lt(item, ONE);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("between 0 and 1");
            }
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTooSmallScale() {
        o.getPrice(ZERO, new BigDecimal(".5"), 0);
    }
}