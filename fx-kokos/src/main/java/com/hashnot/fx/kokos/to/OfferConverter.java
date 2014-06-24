package com.hashnot.fx.kokos.to;

import com.google.gson.Gson;
import com.hashnot.fx.spi.Dir;
import com.hashnot.fx.spi.IMarket;
import com.hashnot.fx.spi.Offer;
import com.hashnot.fx.util.CurrencyPairUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * @author Rafał Krupiński
 */
public class OfferConverter {
    public Offer toOffer(OfferTO offerTo, boolean reversed, IMarket market) {
        String base = reversed ? offerTo.currencyOut : offerTo.currencyIn;
        String counter = reversed ? offerTo.currencyIn : offerTo.currencyOut;
        BigDecimal amount = reversed ? offerTo.amountIn : offerTo.amountOut;

        BigDecimal price = offerTo.course;

        verify(offerTo, reversed);

        return new Offer(amount, price, reversed ? Dir.ask : Dir.bid, base, counter, market);
    }

    private boolean verify(OfferTO offerTo, boolean reversed) {
        BigDecimal price;
        MathContext mc = new MathContext(4, RoundingMode.FLOOR);
        if (reversed)
            price = offerTo.amountIn.divide(offerTo.amountOut, mc);
        else {
            price = offerTo.amountOut.divide(offerTo.amountIn, mc);
        }

        price = price.stripTrailingZeros();
        BigDecimal course = offerTo.course.stripTrailingZeros();
        log.debug("price {}, course {}", price, course);
        return price.equals(course);

    }

    public List<Offer> toOffers(OffersTO container, IMarket market) {
        List<Offer> result = new ArrayList<>(container.offers.size());
        if(container.offers.isEmpty())
            return result;

        OfferTO first = decode(container.offers.get(0));
        boolean reversed = !CurrencyPairUtil.isNonReversed(first.currencyIn, first.currencyOut);

        for (String offer64 : container.offers) {
            OfferTO offerTO = decode(offer64);
            assert samePair(first, offerTO);
            result.add(toOffer(offerTO, reversed, market));
        }
        return result;
    }

    private OfferTO decode(String offer64) {
        byte[] offerJson = Base64.getDecoder().decode(offer64);
        return new Gson().fromJson(new InputStreamReader(new ByteArrayInputStream(offerJson)), OfferTO.class);
    }

    private static boolean samePair(OfferTO first, OfferTO another) {
        return first.currencyIn.equals(another.currencyIn) && first.currencyOut.equals(another.currencyOut);
    }

    private static final Logger log = LoggerFactory.getLogger(OfferConverter.class);
}
