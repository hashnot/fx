package com.hashnot.fx.kokos.to;

import com.google.gson.Gson;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * @author Rafał Krupiński
 */
public class OrderBookConverter {
    private Gson gson = new Gson();

    public OrderBook toOrderBook(OffersTO inAsks, OffersTO inBids, Date timestamp) {
        List<LimitOrder> asks = toLimitOrders(inAsks, timestamp, true);
        List<LimitOrder> bids = toLimitOrders(inBids, timestamp, false);
        return new OrderBook(timestamp, asks, bids);
    }

    private List<LimitOrder> toLimitOrders(OffersTO offers, Date timestamp, boolean reversed) {
        List<LimitOrder> result = new ArrayList<>(offers.offers.size());
        for (String offerStr : offers.offers)
            result.add(toLimitOrder(decode(offerStr), reversed, timestamp));
        return result;
    }

    private OfferTO decode(String offer64) {
        byte[] offerJson = Base64.getDecoder().decode(offer64);
        return gson.fromJson(new InputStreamReader(new ByteArrayInputStream(offerJson)), OfferTO.class);
    }

    public LimitOrder toLimitOrder(OfferTO offerTo, boolean reversed, Date timestamp) {
        String base = reversed ? offerTo.currencyOut : offerTo.currencyIn;
        String counter = reversed ? offerTo.currencyIn : offerTo.currencyOut;
        BigDecimal amount = reversed ? offerTo.amountIn : offerTo.amountOut;


        return new LimitOrder(reversed ? Order.OrderType.ASK : Order.OrderType.BID, amount, new CurrencyPair(base, counter), null, timestamp, offerTo.course.setScale(4));
    }

}
