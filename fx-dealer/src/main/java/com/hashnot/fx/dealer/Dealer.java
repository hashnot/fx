package com.hashnot.fx.dealer;

import com.hashnot.fx.kokos.KokosMarket;
import com.hashnot.fx.spi.*;
import com.hashnot.fx.util.CurrencyPairUtil;
import com.hashnot.fx.wm.WMMarket;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeFactory;
import com.xeiam.xchange.bitcurex.BitcurexExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.ExchangeInfo;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.kraken.KrakenExchange;
import com.xeiam.xchange.service.BaseExchangeService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static com.hashnot.fx.spi.LimitOrderComparator.ORDER_COMPARATOR;
import static com.xeiam.xchange.currency.Currencies.EUR;
import static com.xeiam.xchange.currency.Currencies.PLN;
import static java.lang.System.out;

/**
 * @author Rafał Krupiński
 */
public class Dealer {
    public static void main(String[] args) throws IOException {
        Collection<CurrencyPair> allowedPairs = Arrays.asList(new CurrencyPair(EUR, PLN), CurrencyPair.BTC_PLN, CurrencyPair.BTC_EUR);
        Collection<CurrencyPair> warn = allowedPairs.stream().map(CurrencyPairUtil::reverse).collect(Collectors.toList());

        //Exchange kokos = ExchangeFactory.INSTANCE.createExchange(KokosExchange.class.getName());
        Exchange kraken = ExchangeFactory.INSTANCE.createExchange(KrakenExchange.class.getName());
        Exchange bc = ExchangeFactory.INSTANCE.createExchange(BitcurexExchange.class.getName());

        List<Exchange> exchanges = Arrays.asList(kraken, bc);

        Map<CurrencyPair, Map<Exchange, OrderBook>> orders = new HashMap<>();
        for (Exchange exchange : exchanges) {
            if (exchange == null) continue;
            PollingMarketDataService mds = exchange.getPollingMarketDataService();

            List<CurrencyPair> pairs;

            if (mds instanceof BaseExchangeService)
                pairs = new ArrayList<>(((BaseExchangeService) mds).getExchangeSymbols());
            else {
                ExchangeInfo exchangeInfo = mds.getExchangeInfo();
                pairs = exchangeInfo.getPairs();
            }

            for (CurrencyPair pair : pairs) {
                if (warn.contains(pair)) log.warn("Reverse pair {} from {}", pair, exchange);
                if (!allowedPairs.contains(pair)) continue;

                Map<Exchange, OrderBook> exchangeOrders;
                if (orders.containsKey(pair))
                    exchangeOrders = orders.get(pair);

                else {
                    exchangeOrders = new HashMap<>();
                    orders.put(pair, exchangeOrders);
                }

                OrderBook orderBook = mds.getOrderBook(pair);
                exchangeOrders.put(exchange, orderBook);
            }
        }
        for (Map.Entry<CurrencyPair, Map<Exchange, OrderBook>> e : orders.entrySet()) {
            printReport(e.getKey(), e.getValue());
        }


        if (true)
            return;


        findDeal();

    }

    private static void findDeal() {
        Map<IMarket, IMarketSession> sessions = new HashMap<>();

        KokosMarket kk = new KokosMarket();
        sessions.put(kk, kk.open(null));

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

        Offer newBid = new Offer(maxBid.getValue().amount, minBid.getValue().price.add(new BigDecimal(".001")), Dir.bid, minBid.getValue().base, minBid.getValue().counter, minBid.getKey());
        log.info("open {} @ {}", newBid, newBid.market);
        log.info("Clos {} @ {}", maxBid.getValue(), maxBid.getKey());
    }

    private static void printReport(CurrencyPair pair, Map<Exchange, OrderBook> books) {
        Order.OrderType dir = Order.OrderType.ASK;
        printReport(pair, books, dir);
    }

    private static void printReport(CurrencyPair pair, Map<Exchange, OrderBook> books, Order.OrderType dir) {
        SortedMap<LimitOrder, Exchange> asks = getBestOffers(books, dir);
        log.info("{} summary: best {} {}\tworst {} {}",pair, asks.firstKey(), asks.get(asks.firstKey()), asks.lastKey(), asks.get(asks.lastKey()));
    }

    private static SortedMap<LimitOrder, Exchange> getBestOffers(Map<Exchange, OrderBook> books, Order.OrderType dir) {
        SortedMap<LimitOrder, Exchange> result = new TreeMap<>();
        for (Map.Entry<Exchange, OrderBook> e : books.entrySet()) {
            List<LimitOrder> limitOrders = get(e.getValue(), dir);
            if (limitOrders.isEmpty()) continue;
            Collections.sort(limitOrders, ORDER_COMPARATOR);
            result.put(limitOrders.get(0), e.getKey());
            log.debug("best {}  worst {}", limitOrders.get(0), limitOrders.get(limitOrders.size() - 1));
        }
        return result;
    }

    private static List<LimitOrder> get(OrderBook orderBook, Order.OrderType dir) {
        return dir == Order.OrderType.ASK ? orderBook.getAsks() : orderBook.getBids();
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
