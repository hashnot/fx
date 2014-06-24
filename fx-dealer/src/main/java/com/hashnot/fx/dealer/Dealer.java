package com.hashnot.fx.dealer;

import com.hashnot.fx.cf.CFMarket;
import com.hashnot.fx.kokos.KokosMarket;
import com.hashnot.fx.spi.*;
import com.hashnot.fx.wm.WMMarket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

import static java.lang.System.out;

/**
 * @author Rafał Krupiński
 */
public class Dealer {
    public static void main(String[] args) {
        Map<IMarket, IMarketSession> sessions = new HashMap<>();

        KokosMarket kokos = new KokosMarket();
        sessions.put(kokos, kokos.open(null));

/*
        CFMarket cf = new CFMarket();
        sessions.put(cf, cf.open(null));
*/

        WMMarket wm = new WMMarket();
        sessions.put(wm, wm.open(null));


        Map<IMarket, Offer> ask = new HashMap<>();
        Map<IMarket, Offer> bid = new HashMap<>();
        for (IMarketSession market : sessions.values()) {
            List<Offer> offers = market.getOffers("EUR", "PLN");
            findBestOffers(market.getMarket(), offers, bid, ask);
        }

        for (IMarket market : sessions.keySet()) {
            BigDecimal askPrice = ask.get(market).price;
            BigDecimal bidPrice = bid.get(market).price;
            log.info("{}: ask = {} ({}) bid = {} ({})", market, askPrice, reciprocal(askPrice), bidPrice, reciprocal(bidPrice));
        }

        for (Map.Entry<IMarket, Offer> e : ask.entrySet()) {
            BigDecimal askPrice = e.getValue().price;
            out.print(e.getKey() + " " + askPrice + ": ");
            for (Map.Entry<IMarket, Offer> e2 : bid.entrySet()) {
                BigDecimal bidPrice = e2.getValue().price;
                boolean deal = askPrice.compareTo(bidPrice) > 0;
                if (deal) out.print("***");
                out.print(bidPrice);
                if (deal) out.print("***");
                out.print(" ");
            }
            out.println();
        }

        Map.Entry<IMarket, Offer> minAsk = null, maxAsk = null, minBid = null, maxBid = null;

        for (Map.Entry<IMarket, Offer> e : ask.entrySet()) {
            if (minAsk == null) minAsk = e;
            else minAsk = EntryComparator.min(minAsk, e);

            if (maxAsk == null) maxAsk = e;
            else maxAsk = EntryComparator.max(maxAsk, e);
        }

        for (Map.Entry<IMarket, Offer> e : bid.entrySet()) {
            if (minBid == null) minBid = e;
            else minBid = EntryComparator.min(minBid, e);

            if (maxBid == null) maxBid = e;
            else maxBid = EntryComparator.max(maxBid, e);
        }

        /*
         * there are two ASK and two BID offers: best and worst, on different markets;
          * take the worst offer and open slightly better position
          * when our position is closed, close the best offer on the other market
         */

        log.info("best  ask = {}", minAsk);
        log.info("worst ask = {}", maxAsk);
        Offer newAsk = new Offer(minAsk.getValue().amount, maxAsk.getValue().price.subtract(new BigDecimal(".001")), Dir.ask, maxAsk.getValue().base, maxAsk.getValue().counter, maxAsk.getKey());
        log.info("Open {} @ {}", newAsk, newAsk.market);
        log.info("Clos {} @ {}", minAsk.getValue(), minAsk.getKey());
        log.info("");

        log.info("best  bid = {}", maxBid);
        log.info("worst bid = {}", minBid);

        Offer newBid = new Offer(maxBid.getValue().amount, minBid.getValue().price.     add(new BigDecimal(".001")), Dir.bid, minBid.getValue().base, minBid.getValue().counter, minBid.getKey());
        log.info("open {} @ {}", newBid, newBid.market);
        log.info("Clos {} @ {}", maxBid.getValue(), maxBid.getKey());

    }

    static class EntryComparator implements Comparator<Map.Entry<IMarket, Offer>> {
        public static final EntryComparator COMPARATOR = new EntryComparator();

        @Override
        public int compare(Map.Entry<IMarket, Offer> o1, Map.Entry<IMarket, Offer> o2) {
            return PriceComparator.COMPARATOR.compare(o1.getValue(), o2.getValue());
        }

        public static Map.Entry<IMarket, Offer> min(Map.Entry<IMarket, Offer> o1, Map.Entry<IMarket, Offer> o2) {
            return COMPARATOR.compare(o1, o2) <= 0 ? o1 : o2;
        }

        public static Map.Entry<IMarket, Offer> max(Map.Entry<IMarket, Offer> o1, Map.Entry<IMarket, Offer> o2) {
            return COMPARATOR.compare(o1, o2) >= 0 ? o1 : o2;
        }
    }

    static MathContext c = new MathContext(4, RoundingMode.HALF_UP);

    static BigDecimal reciprocal(BigDecimal in) {
        return BigDecimal.ONE.divide(in, c);
    }

    private static void findBestOffers(IMarket market, List<Offer> offers, Map<IMarket, Offer> bid, Map<IMarket, Offer> ask) {
        for (Offer offer : offers) {
            if (offer.dir == Dir.ask) {
                if (!ask.containsKey(market))
                    ask.put(market, offer);
                else {
                    Offer current = ask.get(market);
                    if (offer.price.compareTo(current.price) > 0)
                        ask.put(market, offer);
                }
            } else {
                if (!bid.containsKey(market))
                    bid.put(market, offer);
                else {
                    Offer current = bid.get(market);
                    if (offer.price.compareTo(current.price) < 0)
                        bid.put(market, offer);
                }

            }
        }
    }

    private static final Logger log = LoggerFactory.getLogger(Dealer.class);
}
