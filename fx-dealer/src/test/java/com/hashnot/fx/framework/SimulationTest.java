package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ExchangeCache;
import com.hashnot.fx.spi.ext.IFeeService;
import com.hashnot.fx.ext.StaticFeeService;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.BaseExchange;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;

import static com.xeiam.xchange.currency.Currencies.*;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.ZERO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimulationTest {
    private static final BigDecimal PERMIL = new BigDecimal(".001");
    private static final BigDecimal TWO = new BigDecimal(2);
    private static final BigDecimal _7 = new BigDecimal(7);
    private static final BigDecimal _8 = new BigDecimal(8);
    private static final BigDecimal _9 = new BigDecimal(9);
    private static final BigDecimal _11 = new BigDecimal(11);
    private static final BigDecimal _12 = new BigDecimal(12);
    private static final BigDecimal _13 = new BigDecimal(13);

    /*
    * opening a position assumes it's immediately closed by a 3rd party.
    * closing a position assumes there is an open 3rd party position
    *
    * closing an ASK should equal opening a BID
    */

    @Test
    public void testOpenAsk0() throws Exception {
        Simulation s = createSimulator();
        assertTrue(s.add(order(ASK, ONE, p1, ONE), e1, order(BID, ONE, p1, ONE), e2));
        assertTrue(s.add(order(ASK, ONE, p1, ONE), e1));
        apply(s);

        //tradableAmount - base
        //price - counter currency
        //ask - offer to sell base

        assertEquals(p1.baseSymbol, _8, get(s, e1, p1.baseSymbol));
        assertEquals(p1.counterSymbol, _12, get(s, e1, p1.counterSymbol));
    }

    private BigDecimal get(Simulation s, Exchange exchange, String symbol) {
        return s.getContext().get(exchange).wallet.get(symbol);
    }

    @Test
    public void testOpenBid0() throws Exception {
        Simulation s = createSimulator();
        assertTrue(s.add(order(ASK, ONE, p1, ONE), e1, order(BID, ONE, p1, ONE), e2));
        assertTrue(s.add(order(BID, ONE, p1, ONE), e2));
        apply(s);

        assertEquals(p1.baseSymbol, _12, get(s, e2, p1.baseSymbol));
        assertEquals(p1.counterSymbol, _8, get(s, e2, p1.counterSymbol));
    }

    @Test
    public void testOpenAsk1() throws Exception {
        Simulation s = createSimulator();
        assertTrue(s.add(order(ASK, ONE, p1, ONE), e1, order(BID, ONE, p1, ONE), e2));
        BigDecimal baseAmount = ONE;
        assertTrue(s.add(order(ASK, baseAmount, p1, TWO), e1));
        apply(s);

        // 2 counter = 1 base
        // should be
        // -1 base
        // +.2 counter

        assertEquals(p1.baseSymbol, _8, get(s, e1, p1.baseSymbol));
        assertEquals(p1.counterSymbol, _13, get(s, e1, p1.counterSymbol));
    }

    @Test
    public void testOpenBid1() throws Exception {
        Simulation s = createSimulator();
        assertTrue(s.add(order(ASK, ONE, p1, ONE), e1, order(BID, ONE, p1, ONE), e2));
        assertTrue(s.add(order(BID, ONE, p1, TWO), e2));
        apply(s);

        // 2 counter = 1 base
        // should be
        // 1 base
        // -.5 counter

        // 10+1+1
        assertEquals(p1.baseSymbol, _12, get(s, e2, p1.baseSymbol));
        // 10-1-2
        assertEquals(p1.counterSymbol, _7, get(s, e2, p1.counterSymbol));
    }

    @Test
    public void testPair0() throws Exception {
        Simulation s = createSimulator();

        assertTrue(s.add(order(ASK, ONE, p1, ONE), e1, order(BID, ONE, p1, ONE), e2));
        apply(s);

        assertEquals(BTC + "@" + e1, _9, get(s, e1, BTC));
        assertEquals(EUR + "@" + e1, _11, get(s, e1, EUR));
        assertEquals(BTC + "@" + e2, _11, get(s, e2, BTC));
        assertEquals(EUR + "@" + e2, _9, get(s, e2, EUR));
    }

    @Test
    public void testOpenAskFee() throws Exception {
        Simulation s = createSimulator(PERMIL);
        assertTrue(s.add(order(ASK, ONE, p1, ONE), e1, order(BID, ONE, p1, ONE), e2));

        assertTrue(s.add(order(ASK, ONE, p1, ONE), e1));
        apply(s);
        s.report();
    }

    @Test
    public void testOpenBidFee() throws Exception {
        Simulation s = createSimulator(PERMIL);
        assertTrue(s.add(order(ASK, ONE, p1, ONE), e1, order(BID, ONE, p1, ONE), e2));

        assertTrue(s.add(order(BID, ONE, p1, ONE), e2));
        apply(s);
        s.report();
    }


    //--------------


    Exchange e1 = new MockExchange();
    Exchange e2 = new MockExchange();

    static CurrencyPair p1 = CurrencyPair.BTC_EUR;

    protected Simulation createSimulator() {
        return createSimulator(ZERO);
    }

    protected Simulation createSimulator(BigDecimal fee) {
        HashMap<Exchange, ExchangeCache> ctx = new HashMap<>();
        IFeeService fs = new StaticFeeService(fee);

        ExchangeCache x1 = new ExchangeCache(fs);
        x1.updateWallet(EUR, TEN);
        x1.updateWallet(BTC, TEN);

        ExchangeCache x2 = new ExchangeCache(fs);
        x2.updateWallet(BTC, TEN);
        x2.updateWallet(EUR, TEN);

        ctx.put(e1, x1);
        ctx.put(e2, x2);
        return new Simulation(ctx);
    }

    // exchange objects are used just as a key
    private static class MockExchange extends BaseExchange {
        static int cnt;

        {
            exchangeSpecification = new ExchangeSpecification((String) null);
            exchangeSpecification.setExchangeName("stub-" + cnt++);
        }

        @Override
        public ExchangeSpecification getDefaultExchangeSpecification() {
            return null;
        }
    }

    protected static LimitOrder order(Order.OrderType type, BigDecimal tradableAmount, CurrencyPair pair, BigDecimal limitPrice) {
        LimitOrder order = new LimitOrder(type, tradableAmount, pair, null, null, limitPrice);
        order.setNetPrice(limitPrice);
        return order;
    }

    protected static void apply(Simulation s){
        LinkedList<OrderUpdateEvent> batch = new LinkedList<>();
        s.apply(batch);
        for (OrderUpdateEvent e : batch) {
            ExchangeCache x = s.getContext().get(e.exchange);
            BigDecimal fee =  x.feeService.getFeePercent(e.order.getCurrencyPair());
            BigDecimal price = Orders.getNetPrice(e.order, fee);

            BigDecimal amountBase = e.order.getTradableAmount();
            BigDecimal amountCounter = e.order.getTradableAmount().multiply(price, Orders.c);

            if (e.order.getType() == Order.OrderType.ASK)
                amountBase = amountBase.negate();
            else
                amountCounter = amountCounter.negate();

            x.updateWallet(e.order.getCurrencyPair().baseSymbol, amountBase);
            x.updateWallet(e.order.getCurrencyPair().counterSymbol, amountCounter);
        }

    }

}