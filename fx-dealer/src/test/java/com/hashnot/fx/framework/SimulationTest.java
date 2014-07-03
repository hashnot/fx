package com.hashnot.fx.framework;

import com.hashnot.fx.IFeeService;
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
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static org.junit.Assert.assertEquals;

public class SimulationTest {
    private static final BigDecimal __ONE = ONE.negate();
    private static final BigDecimal TWO = new BigDecimal(2);
    private static final BigDecimal __TWO = TWO.negate();
    private static final BigDecimal HALF = new BigDecimal(".5");
    private static final BigDecimal __HALF = HALF.negate();

    /*
    * opening a position assumes it's immediately closed by a 3rd party.
    * closing a position assumes there is an open 3rd party position
    *
    * closing an ASK should equal opening a BID
    */

    @Test
    public void testOpenAsk0() throws Exception {
        Simulation s = createSimulator();
        s.open(new LimitOrder(ASK, ONE, p1, null, null, ONE), e1);

        //tradableAmount - base
        //price - counter currency
        //ask - offer to sell base

        assertEquals(__ONE, s.get(e1, p1.baseSymbol));
        assertEquals(ONE, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testOpenBid0() throws Exception {
        Simulation s = createSimulator();
        s.open(new LimitOrder(Order.OrderType.BID, ONE, p1, null, null, ONE), e1);

        assertEquals(ONE, s.get(e1, p1.baseSymbol));
        assertEquals(__ONE, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testOpenAsk1() throws Exception {
        Simulation s = createSimulator();
        BigDecimal baseAmount = ONE;
        BigDecimal price = TWO;
        s.open(new LimitOrder(ASK, baseAmount, p1, null, null, price), e1);

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
        s.open(new LimitOrder(Order.OrderType.BID, baseAmount, p1, null, null, price), e1);

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
        s.close(new LimitOrder(ASK, ONE, p1, null, null, ONE), e1);

        assertEquals(ONE, s.get(e1, p1.baseSymbol));
        assertEquals(__ONE, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testCloseBid0() throws Exception {
        Simulation s = createSimulator();
        s.close(new LimitOrder(Order.OrderType.BID, ONE, p1, null, null, ONE), e1);

        assertEquals(__ONE, s.get(e1, p1.baseSymbol));
        assertEquals(ONE, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testCloseAsk1() throws Exception {
        Simulation s = createSimulator();
        BigDecimal baseAmount = ONE;
        BigDecimal price = TWO;
        s.close(new LimitOrder(ASK, baseAmount, p1, null, null, price), e1);

        assertEquals(ONE, s.get(e1, p1.baseSymbol));
        assertEquals(__TWO, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testCloseBid1() throws Exception {
        Simulation s = createSimulator();
        BigDecimal baseAmount = ONE;
        BigDecimal price = TWO;
        s.close(new LimitOrder(Order.OrderType.BID, baseAmount, p1, null, null, price), e1);

        assertEquals(__ONE, s.get(e1, p1.baseSymbol));
        assertEquals(TWO, s.get(e1, p1.counterSymbol));
    }

    @Test
    public void testPair0() throws Exception {
        Simulation s = createSimulator();
        s.add(e1, BTC, ONE);
        s.add(e1, EUR, TWO);
        s.add(e2, BTC, ONE);
        s.add(e2, EUR, TWO);
        s.report();
        s.open(new LimitOrder(ASK, ONE, p1, null, null, TWO), e1);
        s.report();
        s.close(new LimitOrder(ASK, ONE, p1, null, null, ONE), e2);
        s.report();
    }


    Exchange e1 = new MockExchange();
    Exchange e2 = new MockExchange();

    CurrencyPair p1 = CurrencyPair.BTC_EUR;

    protected Simulation createSimulator() {
        return createSimulator(ZERO);
    }

    protected Simulation createSimulator(BigDecimal fee) {
        HashMap<Exchange, IFeeService> fees = new HashMap<>();
        IFeeService fs = new StaticFeeService(fee);
        fees.put(e1, fs);
        fees.put(e2, fs);
        return new Simulation(fees);
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


}