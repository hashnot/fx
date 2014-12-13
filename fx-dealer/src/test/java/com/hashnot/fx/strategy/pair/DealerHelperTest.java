package com.hashnot.fx.strategy.pair;

import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.account.IWalletMonitor;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.BaseMarketMetadata;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigDecimal;
import java.util.List;

import static com.google.common.collect.Lists.transform;
import static com.hashnot.xchange.ext.util.Comparables.eq;
import static com.xeiam.xchange.dto.Order.OrderType;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class DealerHelperTest {
    private OrderType side;

    public DealerHelperTest(OrderType side) {
        this.side = side;
    }

    @Parameterized.Parameters
    public static Iterable<Object[]> getParams() {
        return transform(asList(OrderType.values()), e -> new Object[]{e});
    }

    /**
     * Standard deal: deal amount: as much as can close, wallet at least 2x
     */
    @Test
    public void testDeal() throws Exception {
        LimitOrder openTempl = order(side, ONE, P, ONE);
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, ONE, P, p(ONE, v("-.1")))
        );
        OrderBinding deal = DealerHelper.deal(openTempl, m1, closeOrders, m2);

        assertNotNull(deal);
        assertTrue(eq(deal.openedOrder.getTradableAmount(), new BigDecimal("1.5")));
    }

    /**
     * Deal with amount of half the wallet @open exchange
     */
    @Test
    public void testDealHalfOpenWallet() throws Exception {
        LimitOrder openTempl = order(side, ONE, P, ONE);
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, TEN, P, p(ONE, v("-.1")))
        );


        OrderBinding deal = DealerHelper.deal(openTempl, m("myOpenExchange", v(2)), closeOrders, m2);

        assertNotNull(deal);
        assertTrue("tradableAmount=" + deal.openedOrder.getTradableAmount(), eq(deal.openedOrder.getTradableAmount(), ONE));
    }

    @Test
    public void testDealHalfCloseWallet() throws Exception {
        LimitOrder openTempl = order(side, ONE, P, ONE);
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, ONE, P, p(ONE, v("-.1")))
        );


        OrderBinding deal = DealerHelper.deal(openTempl, m1, closeOrders, m("myCloseExchange", v(2)));

        assertNotNull(deal);
        assertTrue("tradableAmount=" + deal.openedOrder.getTradableAmount(), eq(deal.openedOrder.getTradableAmount(), ONE));
    }


    @Test
    public void testDealLowOpenWallet() throws Exception {
        LimitOrder openTempl = order(side, ONE, P, ONE);
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, ONE, P, p(ONE, v("-.1")))
        );

        BigDecimal small = v(".01");
        OrderBinding deal = DealerHelper.deal(openTempl, m("myOpenExchange", small), closeOrders, m2);

        if (side == BID) {
            // skip BID
            assertNull(deal);
        } else {
            assertNotNull(deal);
            assertTrue("tradableAmount=" + deal.openedOrder.getTradableAmount(), eq(deal.openedOrder.getTradableAmount(), small));
        }
    }

    @Test
    public void testDealLowCloseWallet() throws Exception {
        LimitOrder openTempl = order(side, ONE, P, ONE);
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, ONE, P, p(ONE, v("-.1")))
        );

        BigDecimal small = v(".01");
        OrderBinding deal = DealerHelper.deal(openTempl, m1, closeOrders, m("myCloseExchange", small));

        if (side == BID) {
            // skip BID
            assertNull(deal);
        } else {
            assertNotNull(deal);
            assertTrue("tradableAmount=" + deal.openedOrder.getTradableAmount(), eq(deal.openedOrder.getTradableAmount(), small));
        }
    }

    @Ignore("fees are currently ignored")
    @Test
    public void testDealFee() throws Exception {
        LimitOrder openTempl = order(side, ONE, P, ONE);
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, ONE, P, p(ONE, v("-.1")))
        );
        OrderBinding deal = DealerHelper.deal(openTempl, m1, closeOrders, m2);

        assertNotNull(deal);
        assertTrue("tradableAmount=" + deal.openedOrder.getTradableAmount(), eq(deal.openedOrder.getTradableAmount(), new BigDecimal("1.5")));
    }

    protected BigDecimal p(BigDecimal base, BigDecimal delta) {
        BigDecimal myDelta = side == OrderType.ASK ? delta : delta.negate();
        return base.add(myDelta);
    }

    protected static BigDecimal v(final String s) {
        return new BigDecimal(s);
    }

    protected static BigDecimal v(long s) {
        return new BigDecimal(s);
    }

    IExchangeMonitor m1 = m("exchange1");
    IExchangeMonitor m2 = m("exchange2");

    private IExchangeMonitor m(String name) {
        return m(name, TEN);
    }

    private IExchangeMonitor m(String name, BigDecimal walletAmount) {
        IWalletMonitor walletMon = mock(IWalletMonitor.class);
        when(walletMon.getWallet(any())).thenReturn(walletAmount);
        return m(name, walletMon);
    }

    private IExchangeMonitor m(String name, IWalletMonitor walletMon) {
        IExchangeMonitor m = mock(IExchangeMonitor.class, name);
        when(m.getWalletMonitor()).thenReturn(walletMon);
        when(m.getMarketMetadata(any())).thenReturn(new BaseMarketMetadata(ONE.movePointLeft(2), 2, ONE.movePointLeft(2)));
        return m;
    }

    static CurrencyPair P = CurrencyPair.BTC_EUR;

    protected static LimitOrder order(OrderType type, BigDecimal tradableAmount, CurrencyPair pair, BigDecimal limitPrice) {
        return new LimitOrder(type, tradableAmount, pair, null, null, limitPrice);
    }

}