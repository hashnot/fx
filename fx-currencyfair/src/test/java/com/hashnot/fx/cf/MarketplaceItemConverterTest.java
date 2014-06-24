package com.hashnot.fx.cf;

import com.hashnot.fx.cf.to.MarketplaceItemConverter;
import com.hashnot.fx.cf.to.MarketplaceItemTO;
import com.hashnot.fx.spi.Dir;
import com.hashnot.fx.spi.Offer;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static java.math.BigDecimal.ONE;
import static org.junit.Assert.*;

public class MarketplaceItemConverterTest {

    private static final String EUR = "EUR";
    private static final String PLN = "PLN";
    private MarketplaceItemConverter conv = new MarketplaceItemConverter();

    @Test
    public void testToOffer() throws Exception {
        MarketplaceItemTO item = new MarketplaceItemTO();
        item.amount = "115,037.01 EUR";
        item.currencyFrom = EUR;
        item.currencyTo = PLN;
        item.increment = new BigDecimal("0.0005");
        BigDecimal rate = new BigDecimal("4.115");
        item.rate = rate;

        Offer offer = conv.toOffer(item, false, null);
        assertEquals(new BigDecimal("115037.01"), offer.amount);
        assertEquals(EUR, offer.base);
        assertEquals(PLN, offer.counter);
        assertEquals(rate, offer.price);
        assertEquals(Dir.bid, offer.dir);
    }

    @Test
    public void testToOfferRev() throws Exception {
        MarketplaceItemTO item = new MarketplaceItemTO();
        item.amount = "115,037.01 PLN";
        item.currencyFrom = PLN;
        item.currencyTo = EUR;
        item.increment = new BigDecimal("0.0005");
        BigDecimal inRate = new BigDecimal("4.115");
        BigDecimal rate = ONE.divide(inRate, 5, RoundingMode.HALF_UP);
        item.rate = inRate;

        Offer offer = conv.toOffer(item, true, null);
        assertEquals(new BigDecimal("115037.01"), offer.amount);
        assertEquals(EUR, offer.base);
        assertEquals(PLN, offer.counter);
        assertEquals(rate, offer.price);
        assertEquals(Dir.ask, offer.dir);
    }

    @Test
    public void testRounding() throws Exception {
        BigDecimal in = new BigDecimal("0.24228");
        assertEquals(in, revert2(in));

        in = new BigDecimal("0.24212");
        assertEquals(in, revert2(in));

    }

    private BigDecimal revert2(BigDecimal in) {
        MathContext mc = new MathContext(5, RoundingMode.HALF_UP);
        BigDecimal rev = ONE.divide(in, mc);
        BigDecimal out = ONE.divide(rev, mc);
        System.out.println(in + " " + out);
        return out;
    }
}
