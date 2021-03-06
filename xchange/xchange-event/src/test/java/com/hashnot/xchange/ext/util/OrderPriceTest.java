package com.hashnot.xchange.ext.util;

import org.junit.Test;

import static com.hashnot.xchange.ext.util.BigDecimals.TWO;
import static com.hashnot.xchange.ext.util.Prices.isCloser;
import static com.hashnot.xchange.ext.util.Prices.isFurther;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static org.junit.Assert.assertTrue;


/**
 * Assert proper interpretation of what price is better
 */
public class OrderPriceTest {

    @Test
    public void testBetterAsk() throws Exception {
        assertTrue(isFurther(TWO, ONE, ASK));
    }

    @Test
    public void testWorseAsk() throws Exception {
        assertTrue(isCloser(ONE, TWO, ASK));
    }

    @Test
    public void testBetterBid() throws Exception {
        assertTrue(isFurther(ONE, TWO, BID));
    }

    @Test
    public void testWorseBid() throws Exception {
        assertTrue(isCloser(TWO, ONE, BID));
    }
}
