package com.hashnot.fx.cf;

import com.hashnot.fx.spi.IMarket;
import com.hashnot.fx.spi.IMarketSession;
import com.hashnot.fx.spi.Offer;
import org.junit.Test;

import java.security.Principal;
import java.util.List;

public class CFMarketTest {

    @Test
    public void testOpen() throws Exception {
        IMarket market = new CFMarket();
        Principal p = new Principal() {
            @Override
            public String getName() {
                return null;
            }
        };
        IMarketSession marketSession = market.open(p);
        List<Offer> offers = marketSession.getOffers("EUR", "PLN");
        for (Offer offer : offers) {
            System.out.println(offer);
        }
    }
}