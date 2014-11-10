package com.hashnot.fx.dealer;

import com.hashnot.fx.framework.Simulation;
import com.hashnot.xchange.event.impl.ExchangeMonitor;
import com.hashnot.xchange.event.impl.exec.IExecutorStrategyFactory;
import com.hashnot.xchange.event.impl.exec.SchedulerExecutorFactory;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.fx.xchange.ext.SimpleExchange;
import com.xeiam.xchange.BaseExchange;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.BaseMarketMetadata;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;

import static com.xeiam.xchange.currency.Currencies.BTC;
import static com.xeiam.xchange.currency.Currencies.EUR;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.*;

// TODO asserts
@Ignore
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
    }

    protected static BigDecimal v(final String s) {
        return new BigDecimal(s);
    }


    Exchange e1;
    Exchange e2;

    static CurrencyPair P = CurrencyPair.BTC_EUR;

    protected Simulation createSimulator(BigDecimal... fee) {
        if (fee.length == 0) fee = new BigDecimal[]{ZERO};

        HashMap<Exchange, IExchangeMonitor> monitors = new HashMap<>();

        IExecutorStrategyFactory executorStrategyFactory = new SchedulerExecutorFactory(Executors.newSingleThreadScheduledExecutor(), 100);
        e1 = new SimpleExchange(new MockExchange(fee[0]));
        ExchangeMonitor m1 = new ExchangeMonitor(e1, executorStrategyFactory);
        m1.getWalletMonitor().getWallet().put(EUR, TEN);
        m1.getWalletMonitor().getWallet().put(BTC, TEN);
        monitors.put(e1, m1);

        e2 = new SimpleExchange(new MockExchange(fee[Math.min(1, fee.length - 1)]));
        ExchangeMonitor m2 = new ExchangeMonitor(e2, executorStrategyFactory);
        m2.getWalletMonitor().getWallet().put(EUR, TEN);
        m2.getWalletMonitor().getWallet().put(BTC, TEN);
        monitors.put(e2, m2);

        return new Simulation(monitors);
    }

    // exchange objects are used just as a key
    private static class MockExchange extends BaseExchange {
        static int cnt;

        public MockExchange(final BigDecimal fee) {
            exchangeSpecification = new ExchangeSpecification((String) null);
            exchangeSpecification.setExchangeName("stub-" + cnt++);
            marketMetadataService = pair -> new BaseMarketMetadata(BigDecimal.ONE.setScale(8).movePointLeft(2), 8, fee);
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