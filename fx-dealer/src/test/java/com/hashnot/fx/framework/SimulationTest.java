package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ExchangeCache;
import com.hashnot.fx.spi.ext.IFeeService;
import com.hashnot.fx.ext.StaticFeeService;
import com.xeiam.xchange.BaseExchange;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.HashMap;

import static com.xeiam.xchange.currency.Currencies.*;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static org.junit.Assert.assertEquals;

public class SimulationTest {
    private static final BigDecimal __ONE = ONE.negate();
    private static final BigDecimal TWO = new BigDecimal(2);
    private static final BigDecimal __TWO = TWO.negate();
    private static final BigDecimal PERMIL = new BigDecimal(".001");

    /*
    * opening a position assumes it's immediately closed by a 3rd party.
    * closing a position assumes there is an open 3rd party position
    *
    * closing an ASK should equal opening a BID
    */

    @Test
    public void testOpenAsk0() throws Exception {
        Simulation s = createSimulator();
        s.open(order(ASK, ONE, p1, ONE), e1);

        //tradableAmount - base
        //price - counter currency
        //ask - offer to sell base

        assertEquals(__ONE, s.get(e1, p1.baseSymbol));
        assertEquals(ONE, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testOpenBid0() throws Exception {
        Simulation s = createSimulator();
        s.open(order(BID, ONE, p1, ONE), e1);

        assertEquals(ONE, s.get(e1, p1.baseSymbol));
        assertEquals(__ONE, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testOpenAsk1() throws Exception {
        Simulation s = createSimulator();
        BigDecimal baseAmount = ONE;
        BigDecimal price = TWO;
        s.open(order(ASK, baseAmount, p1, price), e1);

        // 2 counter = 1 base
        // should be
        // -1 base
        // +.2 counter

        assertEquals(__ONE, s.get(e1, p1.baseSymbol));
        assertEquals(TWO, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testOpenBid1() throws Exception {
        Simulation s = createSimulator();
        BigDecimal baseAmount = ONE;
        BigDecimal price = TWO;
        s.open(order(BID, baseAmount, p1, price), e1);

        // 2 counter = 1 base
        // should be
        // 1 base
        // -.5 counter

        assertEquals(ONE, s.get(e1, p1.baseSymbol));
        assertEquals(__TWO, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testCloseAsk0() throws Exception {
        Simulation s = createSimulator();
        s.close(order(ASK, ONE, p1, ONE), e1);

        assertEquals(ONE, s.get(e1, p1.baseSymbol));
        assertEquals(__ONE, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testCloseBid0() throws Exception {
        Simulation s = createSimulator();
        s.close(order(BID, ONE, p1, ONE), e1);

        assertEquals(__ONE, s.get(e1, p1.baseSymbol));
        assertEquals(ONE, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testCloseAsk1() throws Exception {
        Simulation s = createSimulator();
        BigDecimal baseAmount = ONE;
        BigDecimal price = TWO;
        s.close(order(ASK, baseAmount, p1, price), e1);

        assertEquals(ONE, s.get(e1, p1.baseSymbol));
        assertEquals(__TWO, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testCloseBid1() throws Exception {
        Simulation s = createSimulator();
        BigDecimal baseAmount = ONE;
        BigDecimal price = TWO;
        s.close(order(BID, baseAmount, p1, price), e1);

        assertEquals(__ONE, s.get(e1, p1.baseSymbol));
        assertEquals(TWO, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testPair0() throws Exception {
        Simulation s = createSimulator();
        s.add(e1, BTC, ZERO);
        s.add(e1, EUR, ZERO);
        s.add(e2, BTC, ZERO);
        s.add(e2, EUR, ZERO);

        LimitOrder close = order(ASK, ONE, p1, ONE);
        LimitOrder open = order(ASK, ONE, p1, TWO);
        s.pair(close, e2, open, e1);

        assertEquals(__ONE, s.get(e1, BTC));
        assertEquals(TWO, s.get(e1, EUR));
        assertEquals(ONE, s.get(e2, BTC));
        assertEquals(__ONE, s.get(e2, EUR));
    }

    @Test
    public void testOpenAskFee() throws Exception {
        Simulation s = createSimulator(PERMIL);

        s.open(order(ASK, ONE, p1, ONE), e1);
        s.report();
    }

    @Test
    public void testOpenBidFee() throws Exception {
        Simulation s = createSimulator(PERMIL);

        s.open(order(BID, ONE, p1, ONE), e1);
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
        ExchangeCache cache = new ExchangeCache(fs);
        ctx.put(e1, cache);
        ctx.put(e2, cache);
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
        return new LimitOrder(type, tradableAmount, pair, null, null, limitPrice);
    }

}