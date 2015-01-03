package com.hashnot.fx.strategy.pair;

import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.account.IWalletMonitor;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.BaseMarketMetadata;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.transform;
import static com.hashnot.xchange.ext.util.Comparables.eq;
import static com.xeiam.xchange.dto.Order.OrderType;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class DealerHelperTest {
    private OrderType side;
    private DealerConfig config;
    private SimpleOrderOpenStrategy s = new SimpleOrderOpenStrategy();

    public DealerHelperTest(OrderType side) {
        this.side = side;
        config = new DealerConfig(side, P);
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
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, ONE, P, p(ONE, v("-.1")))
        );
        DealerData data = new DealerData(m1, m2, emptyMap());
        data.closingOrders = closeOrders;

        LimitOrder deal = DealerHelper.deal(config, data, ONE, s);

        assertNotNull(deal);
        BigDecimal amount = deal.getTradableAmount();
        assertTrue(amount.toString(), eq(amount, ONE));
    }

    /**
     * Deal with amount of half the wallet @open exchange
     */
    @Test
    public void testDealHalfOpenWallet() throws Exception {
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, TEN, P, p(ONE, v("-.1")))
        );


        DealerData data = new DealerData(m("myOpenExchange", v(2)), m2, new HashMap<>());
        data.closingOrders = closeOrders;

        LimitOrder deal = DealerHelper.deal(config, data, ONE, s);

        assertNotNull(deal);
        assertTrue("tradableAmount=" + deal.getTradableAmount(), eq(deal.getTradableAmount(), ONE));
    }

    @Test
    public void testDealHalfCloseWallet() throws Exception {
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, ONE, P, p(ONE, v("-.1")))
        );


        DealerData data = new DealerData(m1, m("myCloseExchange", v(2)), new HashMap<>());
        data.closingOrders = closeOrders;

        LimitOrder deal = DealerHelper.deal(config, data, ONE, s);

        assertNotNull(deal);
        assertTrue("tradableAmount=" + deal.getTradableAmount(), eq(deal.getTradableAmount(), ONE));
    }


    @Test
    public void testDealLowOpenWallet() throws Exception {
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, ONE, P, p(ONE, v("-.1")))
        );

        BigDecimal small = v(".01");
        DealerData data = new DealerData(m("myOpenExchange", small), m2, emptyMap());
        data.closingOrders = closeOrders;

        LimitOrder deal = DealerHelper.deal(config, data, ONE, s);

        if (side == BID) {
            // skip BID
            assertNull(deal);
        } else {
            assertNotNull(deal);
            assertTrue("tradableAmount=" + deal.getTradableAmount(), eq(deal.getTradableAmount(), small));
        }
    }

    @Test
    public void testDealLowCloseWallet() throws Exception {
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, ONE, P, p(ONE, v("-.1")))
        );

        BigDecimal small = v(".01");
        DealerData data = new DealerData(m1, m("myCloseExchange", small), emptyMap());
        data.closingOrders = closeOrders;

        LimitOrder deal = DealerHelper.deal(config, data, ONE, s);

        if (side == BID) {
            // skip BID
            assertNull(deal);
        } else {
            assertNotNull(deal);
            assertTrue("tradableAmount=" + deal.getTradableAmount(), eq(deal.getTradableAmount(), small));
        }
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
        IWalletMonitor walletMon = mock(IWalletMonitor.class, "Monitor:" + name);
        when(walletMon.getWallet(any())).thenReturn(walletAmount);
        Map<String, BigDecimal> wallet = mock(Map.class);
        when(wallet.get(any())).thenReturn(walletAmount);
        when(walletMon.getWallet()).thenReturn(wallet);
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