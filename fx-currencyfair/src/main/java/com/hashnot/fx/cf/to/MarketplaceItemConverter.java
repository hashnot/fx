package com.hashnot.fx.cf.to;

import com.hashnot.fx.spi.Dir;
import com.hashnot.fx.spi.IMarket;
import com.hashnot.fx.spi.Offer;
import com.hashnot.fx.util.CurrencyPairUtil;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Rafał Krupiński
 */
public class MarketplaceItemConverter {
    private DecimalFormat decimalFormat;
    public static final MathContext mc = new MathContext(5, RoundingMode.HALF_UP);

    {
        decimalFormat = new DecimalFormat("#,##0.0#", new DecimalFormatSymbols(Locale.ENGLISH));
        decimalFormat.setParseBigDecimal(true);
    }

    public Offer toOffer(MarketplaceItemTO item, boolean reversed, IMarket market) {
        int split = item.amount.indexOf(' ');
        try {
            BigDecimal amount = (BigDecimal) decimalFormat.parse(item.amount.substring(0, split));
            BigDecimal rate = item.rate;
            if (reversed)
                rate = BigDecimal.ONE.divide(rate, mc);

            String base = reversed ? item.currencyTo : item.currencyFrom;
            String counter = reversed ? item.currencyFrom : item.currencyTo;
            Dir dir = reversed ? Dir.bid : Dir.ask;
            return new Offer(amount, rate, dir, base, counter, market);
        } catch (ParseException e) {
            throw new IllegalArgumentException(item.toString(), e);
        }
    }

    public List<Offer> toOffers(MarketContainer container, IMarket market) {
        List<MarketplaceItemTO> input = container.market.marketplaceItemsAvail;
        List<Offer> result = new ArrayList<>(input.size());
        if (input.isEmpty())
            return result;
        MarketplaceItemTO first = input.get(0);
        boolean reversed = !CurrencyPairUtil.isNonReversed(first.currencyFrom, first.currencyTo);

        for (MarketplaceItemTO item : input) {
            assert samePair(first, item) : "Different currency pairs in single offersTO";
            result.add(toOffer(item, reversed, market));
        }
        return result;
    }

    private static boolean samePair(MarketplaceItemTO first, MarketplaceItemTO another) {
        return first.currencyFrom.equals(another.currencyFrom) && first.currencyTo.equals(another.currencyTo);
    }
}
