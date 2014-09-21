package com.hashnot.fx;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.math.BigDecimal;

import static com.hashnot.fx.util.Numbers.BigDecimal._ONE;
import static com.hashnot.fx.util.Orders.withAmount;
import static java.math.BigDecimal.ONE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OrderBookUpdateEventTest {

    private static final BigDecimal TWO = new BigDecimal(2);
    private static final CurrencyPair P = CurrencyPair.BTC_EUR;

    @Test
    public void testSame() throws Exception {
        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        OrderBook before = new OrderBook(null, asList(one), emptyList());
        OrderBook after = new OrderBook(null, asList(one), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(P, before, after).getChanges();
        assertTrue(changes.getAsks().isEmpty());
    }

    @Test
    public void testSamePrice() throws Exception {
        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder two = new LimitOrder(Order.OrderType.ASK, TWO, P, null, null, ONE);

        OrderBook before = new OrderBook(null, asList(one), emptyList());
        OrderBook after = new OrderBook(null, asList(two), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(P, before, after).getChanges();

        assertEquals(asList(one), changes.getAsks());
    }

    @Test
    public void testEmpty() throws Exception {
        OrderBook before = new OrderBook(null, emptyList(), emptyList());
        OrderBook after = new OrderBook(null, emptyList(), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(P, before, after).getChanges();

        assertTrue(changes.getAsks().isEmpty());
    }

    @Test
    public void testEmptyBefore() throws Exception {
        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);

        OrderBook before = new OrderBook(null, emptyList(), emptyList());
        OrderBook after = new OrderBook(null, asList(one), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(P, before, after).getChanges();

        assertEquals(asList(one), changes.getAsks());
    }

    @Test
    public void testEmptyAfter() throws Exception {
        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);

        OrderBook before = new OrderBook(null, asList(one), emptyList());
        OrderBook after = new OrderBook(null, emptyList(), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(P, before, after).getChanges();

        assertEquals(asList(withAmount(one, _ONE)), changes.getAsks());
    }

    @Test
    public void testDiffPrice() throws Exception {
        LimitOrder oneOne = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder oneTwo = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, TWO);

        OrderBook before = new OrderBook(null, asList(oneOne), emptyList());
        OrderBook after = new OrderBook(null, asList(oneTwo), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(P, before, after).getChanges();

        assertEquals(asList(withAmount(oneOne, _ONE), oneTwo), changes.getAsks());
    }

    @Test
    public void testAddition() throws Exception {
        LimitOrder oneOne = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder twoTwo = new LimitOrder(Order.OrderType.ASK, TWO, P, null, null, TWO);
        LimitOrder oneTwo = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, TWO);

        OrderBook before = new OrderBook(null, asList(oneOne, oneTwo), emptyList());
        OrderBook after = new OrderBook(null, asList(oneOne, twoTwo), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(P, before, after).getChanges();

        assertEquals(asList(oneTwo), changes.getAsks());
    }

    @Test
    public void testSubtraction() throws Exception {
        LimitOrder oneOne = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder twoTwo = new LimitOrder(Order.OrderType.ASK, TWO, P, null, null, TWO);
        LimitOrder oneTwo = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, TWO);
        LimitOrder _oneTwo = new LimitOrder(Order.OrderType.ASK, _ONE, P, null, null, TWO);

        OrderBook before = new OrderBook(null, asList(oneOne, twoTwo), emptyList());
        OrderBook after = new OrderBook(null, asList(oneOne, oneTwo), emptyList());

        OrderBook changes = new OrderBookUpdateEvent(P, before, after).getChanges();

        assertEquals(asList(_oneTwo), changes.getAsks());
    }

}