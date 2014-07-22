package com.hashnot.fx.dealer;

import com.hashnot.fx.ext.StaticFeeService;
import com.hashnot.fx.framework.Simulation;
import com.hashnot.fx.spi.ExchangeCache;
import com.xeiam.xchange.BaseExchange;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.xeiam.xchange.currency.Currencies.BTC;
import static com.xeiam.xchange.currency.Currencies.EUR;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.*;

public class SimulationTest {

    @Test
    public void testAskDeal() throws Exception {
        Simulation s = createSimulator();

        LimitOrder worst = order(ASK, ONE, P, ONE);
        List<LimitOrder> bestOrders = Arrays.asList(
                order(ASK, v(".5"), P, v(".8")),
                order(ASK, ONE, P, v(".9"))
        );
        s.deal(worst, e1, bestOrders, e2);
        s.report();
    }

    @Test
    public void testBidDeal() throws Exception {
        Simulation s = createSimulator();

        LimitOrder worst = order(BID, ONE, P, ONE);
        List<LimitOrder> bestOrders = Arrays.asList(
                order(BID, v(".5"), P, v("1.1")),
                order(BID, ONE, P, v("1.2"))
        );
        s.deal(worst, e1, bestOrders, e2);
        s.report();
    }

    @Test
    public void testAskDealFee() throws Exception {
        Simulation s = createSimulator(v(".2"), v(".1"));

        LimitOrder worst = order(ASK, ONE, P, ONE);
        List<LimitOrder> bestOrders = Arrays.asList(
                order(ASK, v(".5"), P, v(".8")),
                order(ASK, ONE, P, v(".9"))
        );
        s.deal(worst, e1, bestOrders, e2);
        s.report();
    }

    @Test
    public void testBidDealFee() throws Exception {
        Simulation s = createSimulator(v(".2"), v(".1"));

        LimitOrder worst = order(BID, ONE, P, ONE);
        List<LimitOrder> bestOrders = Arrays.asList(
                order(BID, v(".5"), P, v("1.2")),
                order(BID, ONE, P, v("1.1"))
        );
        s.deal(worst, e1, bestOrders, e2);
        s.report();
    }

    protected static BigDecimal v(final String s) {
        return new BigDecimal(s);
    }

    protected static BigDecimal v(long s) {
        return new BigDecimal(s);
    }


    Exchange e1 = new MockExchange();
    Exchange e2 = new MockExchange();

    static CurrencyPair P = CurrencyPair.BTC_EUR;

    protected Simulation createSimulator(BigDecimal... fee) {
        if (fee.length == 0) fee = new BigDecimal[]{ZERO};
        HashMap<Exchange, ExchangeCache> ctx = new HashMap<>();

        ExchangeCache x1 = new ExchangeCache(e1.toString(), new StaticFeeService(fee[0]));
        x1.updateWallet(EUR, TEN);
        x1.updateWallet(BTC, TEN);

        ExchangeCache x2 = new ExchangeCache(e2.toString(), new StaticFeeService(fee[Math.min(1, fee.length - 1)]));
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

}