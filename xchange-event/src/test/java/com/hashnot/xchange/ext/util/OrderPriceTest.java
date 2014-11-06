package com.hashnot.xchange.ext.util;

import org.junit.Test;

import java.math.BigDecimal;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static org.junit.Assert.assertTrue;


/**
 * Assert proper interpretation of what price is better
 */
public class OrderPriceTest {

    private static BigDecimal TWO = new BigDecimal(2);

    @Test
    public void testBetterAsk() throws Exception {
        assertTrue(Numbers.Price.isBetter(TWO, ONE, ASK));
    }

    @Test
    public void testWorseAsk() throws Exception {
        assertTrue(Numbers.Price.isWorse(ONE, TWO, ASK));
    }

    @Test
    public void testBetterBid() throws Exception {
        assertTrue(Numbers.Price.isBetter(ONE, TWO, BID));
    }

    @Test
    public void testWorseBid() throws Exception {
        assertTrue(Numbers.Price.isWorse(TWO, ONE, BID));
    }
}