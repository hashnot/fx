package com.hashnot.fx.wm.to;

import com.hashnot.fx.spi.Dir;
import com.hashnot.fx.spi.IMarket;
import com.hashnot.fx.spi.Offer;
import com.hashnot.fx.util.CurrencyPairUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Rafał Krupiński
 */
public class OfferConverter {
    public List<Offer> toOffers(OffersTO offers, IMarket market) {
        List<OfferTO> input = offers.offers;
        List<Offer> result = new ArrayList<>(input.size());
        if (input.isEmpty())
            return result;

        OfferTO first = input.get(0);

        for (OfferTO to : input) {
            assert samePair(first, to);
            Offer o = toOffer(market, to);
            result.add(o);
        }
        return result;
    }

    private Offer toOffer(IMarket market, OfferTO to) {
        boolean reversed = CurrencyPairUtil.isNonReversed(to.from, to.to);
        Dir dir = reversed ? Dir.bid : Dir.ask;
        String base = reversed ? to.from : to.to;
        String counter = reversed ? to.to : to.from;
        return new Offer(to.ammount, to.rate, dir, base, counter, market);
    }

    private boolean samePair(OfferTO first, OfferTO other) {
        String a1 = first.from;
        String b1 = other.from;
        String a2 = first.to;
        String b2 = other.to;
        return (a1.equals(b1) && a2.equals(b2)) || (a1.equals(b2) && a2.equals(b1));
    }

    private static final Logger log = LoggerFactory.getLogger(OfferConverter.class);
}
