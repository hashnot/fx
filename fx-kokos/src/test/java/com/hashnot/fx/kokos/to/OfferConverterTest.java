package com.hashnot.fx.kokos.to;

import com.hashnot.fx.spi.Dir;
import com.hashnot.fx.spi.Offer;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class OfferConverterTest {
    private static final String USD = "USD";
    private static final String PLN = "PLN";
    OfferConverter conv = new OfferConverter();

    @Test
    public void testStraight() throws Exception {
        OfferTO in = new OfferTO();
        BigDecimal price = new BigDecimal("3.0625");
        in.course = price;
        in.currencyIn = USD;
        BigDecimal amount = new BigDecimal("1184.42");
        in.amountIn = new BigDecimal("386.75");
        in.amountOut = amount;
        in.currencyOut = PLN;

        Offer expected = new Offer(amount, price, Dir.bid, USD, PLN, null);

        Offer out = conv.toOffer(in, false, null);
        assertEquals(expected, out);
    }

    @Test
    public void testReversed() throws Exception {
        OfferTO in = new OfferTO();
        BigDecimal priceRev = new BigDecimal("3.0536");
        in.course = priceRev;
        in.currencyIn = PLN;
        BigDecimal amount = new BigDecimal("1000");
        in.amountIn = amount;
        in.amountOut = new BigDecimal("327.48");
        in.currencyOut = USD;

        Offer expected = new Offer(amount, priceRev, Dir.ask, USD, PLN, null);

        Offer out = conv.toOffer(in, true, null);
        assertEquals(expected, out);
    }
}