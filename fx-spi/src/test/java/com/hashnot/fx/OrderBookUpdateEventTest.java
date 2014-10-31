package com.hashnot.fx;

import com.hashnot.fx.ext.Market;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.math.BigDecimal;

import static com.hashnot.fx.util.Numbers.BigDecimal._ONE;
import static com.hashnot.fx.util.Orders.withAmount;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OrderBookUpdateEventTest {

    private static final BigDecimal TWO = new BigDecimal(2);
    private static final CurrencyPair P = CurrencyPair.BTC_EUR;
    private static final Market m = new Market(null, P);

    @Test
    public void testSame() throws Exception {
        LimitOrder ask = new LimitOrder(ASK, ONE, P, null, null, ONE);
        LimitOrder bid = new LimitOrder(BID, ONE, P, null, null, ONE);
        OrderBook before = new OrderBook(null, asList(ask), asList(bid));
        OrderBook after = new OrderBook(null, asList(ask), asList(bid));

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();
        assertTrue(changes.getOrders(ASK).isEmpty());
        assertTrue(changes.getOrders(BID).isEmpty());
    }

    @Test
    public void testSamePriceAsk() throws Exception {
        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder two = new LimitOrder(Order.OrderType.ASK, TWO, P, null, null, ONE);

        OrderBook before = new OrderBook(null, asList(one), emptyList());
        OrderBook after = new OrderBook(null, asList(two), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertEquals(asList(one), changes.getAsks());
    }

    @Test
    public void testSamePriceBid() throws Exception {
        LimitOrder one = new LimitOrder(BID, ONE, P, null, null, ONE);
        LimitOrder two = new LimitOrder(BID, TWO, P, null, null, ONE);

        OrderBook before = new OrderBook(null, emptyList(), asList(one));
        OrderBook after = new OrderBook(null, emptyList(), asList(two));

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertEquals(asList(one), changes.getOrders(BID));
    }

    @Test
    public void testEmpty() throws Exception {
        OrderBook before = new OrderBook(null, emptyList(), emptyList());
        OrderBook after = new OrderBook(null, emptyList(), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertTrue(changes.getOrders(ASK).isEmpty());
        assertTrue(changes.getOrders(BID).isEmpty());
    }

    @Test
    public void testEmptyBeforeAsk() throws Exception {
        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);

        OrderBook before = new OrderBook(null, emptyList(), emptyList());
        OrderBook after = new OrderBook(null, asList(one), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertEquals(asList(one), changes.getAsks());
    }

    @Test
    public void testEmptyBeforeBid() throws Exception {
        LimitOrder one = new LimitOrder(BID, ONE, P, null, null, ONE);

        OrderBook before = new OrderBook(null, emptyList(), emptyList());
        OrderBook after = new OrderBook(null, emptyList(), asList(one));

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertEquals(asList(one), changes.getOrders(BID));
    }

    @Test
    public void testEmptyAfterAsk() throws Exception {
        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);

        OrderBook before = new OrderBook(null, asList(one), emptyList());
        OrderBook after = new OrderBook(null, emptyList(), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertEquals(asList(withAmount(one, _ONE)), changes.getAsks());
    }

    @Test
    public void testEmptyAfterBid() throws Exception {
        LimitOrder one = new LimitOrder(BID, ONE, P, null, null, ONE);

        OrderBook before = new OrderBook(null, emptyList(), asList(one));
        OrderBook after = new OrderBook(null, emptyList(), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertEquals(asList(withAmount(one, _ONE)), changes.getOrders(BID));
    }

    @Test
    public void testDiffPriceAsk() throws Exception {
        LimitOrder oneOne = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder oneTwo = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, TWO);

        OrderBook before = new OrderBook(null, asList(oneOne), emptyList());
        OrderBook after = new OrderBook(null, asList(oneTwo), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertEquals(asList(withAmount(oneOne, _ONE), oneTwo), changes.getAsks());
    }

    @Test
    public void testDiffPriceBid() throws Exception {
        LimitOrder oneOne = new LimitOrder(BID, ONE, P, null, null, ONE);
        LimitOrder oneTwo = new LimitOrder(BID, ONE, P, null, null, TWO);
        LimitOrder one_one = new LimitOrder(BID, _ONE, P, null, null, ONE);

        OrderBook before = new OrderBook(null, emptyList(), asList(oneOne));
        OrderBook after = new OrderBook(null, emptyList(), asList(oneTwo));

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertEquals(asList(oneTwo, one_one), changes.getOrders(BID));
    }

    @Test
    public void testAdditionAsk() throws Exception {
        LimitOrder oneOne = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder twoTwo = new LimitOrder(Order.OrderType.ASK, TWO, P, null, null, TWO);
        LimitOrder oneTwo = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, TWO);

        OrderBook before = new OrderBook(null, asList(oneOne, oneTwo), emptyList());
        OrderBook after = new OrderBook(null, asList(oneOne, twoTwo), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertEquals(asList(oneTwo), changes.getAsks());
    }

    @Test
    public void testAdditionBid() throws Exception {
        LimitOrder oneOne = new LimitOrder(BID, ONE, P, null, null, ONE);
        LimitOrder twoTwo = new LimitOrder(BID, TWO, P, null, null, TWO);
        LimitOrder oneTwo = new LimitOrder(BID, ONE, P, null, null, TWO);

        OrderBook before = new OrderBook(null, emptyList(), asList(oneOne, oneTwo));
        OrderBook after = new OrderBook(null, emptyList(), asList(oneOne, twoTwo));

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertEquals(asList(oneTwo), changes.getOrders(BID));
    }

    @Test
    public void testSubtractionAsk() throws Exception {
        LimitOrder oneOne = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder twoTwo = new LimitOrder(Order.OrderType.ASK, TWO, P, null, null, TWO);
        LimitOrder oneTwo = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, TWO);
        LimitOrder _oneTwo = new LimitOrder(Order.OrderType.ASK, _ONE, P, null, null, TWO);

        OrderBook before = new OrderBook(null, asList(oneOne, twoTwo), emptyList());
        OrderBook after = new OrderBook(null, asList(oneOne, oneTwo), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertEquals(asList(_oneTwo), changes.getAsks());
    }

    @Test
    public void testSubtraction() throws Exception {
        LimitOrder oneOne = new LimitOrder(BID, ONE, P, null, null, ONE);
        LimitOrder twoTwo = new LimitOrder(BID, TWO, P, null, null, TWO);
        LimitOrder oneTwo = new LimitOrder(BID, ONE, P, null, null, TWO);
        LimitOrder _oneTwo = new LimitOrder(BID, _ONE, P, null, null, TWO);

        OrderBook before = new OrderBook(null, emptyList(), asList(oneOne, twoTwo));
        OrderBook after = new OrderBook(null, emptyList(), asList(oneOne, oneTwo));

        OrderBook changes = new OrderBookUpdateEvent(m, before, after).getChanges();

        assertEquals(asList(_oneTwo), changes.getOrders(BID));
    }

}