package com.hashnot.fx.kokos;

import com.hashnot.fx.spi.Offer;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class KokosMarketSessionTest {

    @Test
    public void testGetOffers() throws Exception {
        List<Offer> offers = new KokosMarket().open(null).getOffers("EUR", "PLN");
        for (Offer offer : offers) {
            System.out.println(offer);
        }
    }
}