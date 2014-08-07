package com.hashnot.fx.dealer;

import com.hashnot.fx.ext.StaticFeeService;
import com.hashnot.fx.framework.Simulation;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.spi.ext.SimpleExchange;
import com.hashnot.fx.util.Exchanges;
import com.hashnot.fx.util.exec.IExecutorStrategyFactory;
import com.hashnot.fx.util.exec.SchedulerExecutorFactory;
import com.xeiam.xchange.BaseExchange;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

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
        report();
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
        report();
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
        report();
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
        report();
    }

    protected void report() {
        Exchanges.report(Arrays.asList(e1, e2));
    }

    protected static BigDecimal v(final String s) {
        return new BigDecimal(s);
    }


    IExchange e1;
    IExchange e2;

    static CurrencyPair P = CurrencyPair.BTC_EUR;

    protected Simulation createSimulator(BigDecimal... fee) {
        if (fee.length == 0) fee = new BigDecimal[]{ZERO};

        IExecutorStrategyFactory executorStrategyFactory = new SchedulerExecutorFactory(Executors.newSingleThreadScheduledExecutor(), 100);
        e1 = new SimpleExchange(new MockExchange(), new StaticFeeService(fee[0]), executorStrategyFactory, null, null, 5, 8);
        e1.getWallet().put(EUR, TEN);
        e1.getWallet().put(BTC, TEN);

        e2 = new SimpleExchange(new MockExchange(), new StaticFeeService(fee[Math.min(1, fee.length - 1)]), executorStrategyFactory, null, null, 5, 8);
        e2.getWallet().put(EUR, TEN);
        e2.getWallet().put(BTC, TEN);

        return new Simulation();
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