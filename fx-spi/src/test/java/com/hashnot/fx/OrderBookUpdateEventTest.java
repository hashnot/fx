package com.hashnot.fx;

import com.hashnot.fx.ext.Market;
import com.hashnot.fx.ext.MarketSide;
import com.hashnot.fx.ext.OrderBookSideUpdateEvent;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.hashnot.fx.util.Numbers.BigDecimal._ONE;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static java.math.BigDecimal.ONE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OrderBookUpdateEventTest {

    private static final BigDecimal TWO = new BigDecimal(2);
    private static final CurrencyPair P = CurrencyPair.BTC_EUR;
    private static final Market m = new Market(null, P);
    private static final MarketSide ms = new MarketSide(m, ASK);

    @Test
    public void testSame() throws Exception {
        LimitOrder ask = new LimitOrder(ASK, ONE, P, null, null, ONE);
        List<LimitOrder> before = asList(ask);
        List<LimitOrder> after = asList(ask);

        List<LimitOrder> changes = new OrderBookSideUpdateEvent(ms, before, after).getChanges();
        assertTrue(changes.isEmpty());
    }

    @Test
    public void testSamePrice() throws Exception {
        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder two = new LimitOrder(Order.OrderType.ASK, TWO, P, null, null, ONE);

        List<LimitOrder> before = asList(one);
        List<LimitOrder> after = asList(two);

        List<LimitOrder> changes = new OrderBookSideUpdateEvent(ms, before, after).getChanges();

        assertEquals(asList(one), changes);
    }

    @Test
    public void testEmpty() throws Exception {
        List<LimitOrder> before = emptyList();
        List<LimitOrder> after = emptyList();

        List<LimitOrder> changes = new OrderBookSideUpdateEvent(ms, before, after).getChanges();

        assertTrue(changes.isEmpty());
    }

    @Test
    public void testEmptyBefore() throws Exception {
        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);

        List<LimitOrder> before = emptyList();
        List<LimitOrder> after = asList(one);

        List<LimitOrder> changes = new OrderBookSideUpdateEvent(ms, before, after).getChanges();

        assertEquals(asList(one), changes);
    }

    @Test
    public void testEmptyAfter() throws Exception {
        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder _oneOne = new LimitOrder(Order.OrderType.ASK, _ONE, P, null, null, ONE);

        List<LimitOrder> before = asList(one);
        List<LimitOrder> after = emptyList();

        List<LimitOrder> changes = new OrderBookSideUpdateEvent(ms, before, after).getChanges();

        assertEquals(asList(_oneOne), changes);
    }

    @Test
    public void testDiffPrice() throws Exception {
        LimitOrder oneOne = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder oneTwo = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, TWO);
        LimitOrder _oneOne = new LimitOrder(Order.OrderType.ASK, _ONE, P, null, null, ONE);

        List<LimitOrder> before = asList(oneOne);
        List<LimitOrder> after = asList(oneTwo);

        List<LimitOrder> changes = new OrderBookSideUpdateEvent(ms, before, after).getChanges();

        assertEquals(asList(_oneOne, oneTwo), changes);
    }

    @Test
    public void testAddition() throws Exception {
        LimitOrder oneOne = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder twoTwo = new LimitOrder(Order.OrderType.ASK, TWO, P, null, null, TWO);
        LimitOrder oneTwo = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, TWO);

        List<LimitOrder> before = asList(oneOne, oneTwo);
        List<LimitOrder> after = asList(oneOne, twoTwo);

        List<LimitOrder> changes = new OrderBookSideUpdateEvent(ms, before, after).getChanges();

        assertEquals(asList(oneTwo), changes);
    }

    @Test
    public void testSubtraction() throws Exception {
        LimitOrder oneOne = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder twoTwo = new LimitOrder(Order.OrderType.ASK, TWO, P, null, null, TWO);
        LimitOrder oneTwo = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, TWO);
        LimitOrder _oneTwo = new LimitOrder(Order.OrderType.ASK, _ONE, P, null, null, TWO);

        List<LimitOrder> before = asList(oneOne, twoTwo);
        List<LimitOrder> after = asList(oneOne, oneTwo);

        List<LimitOrder> changes = new OrderBookSideUpdateEvent(ms, before, after).getChanges();

        assertEquals(asList(_oneTwo), changes);
    }
}
