package com.hashnot.fx.dealer;

import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.IWalletMonitor;
import com.hashnot.xchange.ext.util.Numbers;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.BaseMarketMetadata;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

import static com.google.common.collect.Lists.transform;
import static com.xeiam.xchange.dto.Order.OrderType;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class DealerHelperTest {
    final private static Logger log = LoggerFactory.getLogger(DealerHelperTest.class);
    private OrderType side;

    public DealerHelperTest(OrderType side) {
        this.side = side;
    }

    @Parameterized.Parameters
    public static Iterable<Object[]> getParams() {
        return transform(asList(OrderType.values()), e -> new Object[]{e});
    }

    @Test
    public void testDeal() throws Exception {
        LimitOrder openTempl = order(side, ONE, P, ONE, m1);
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2")), m2),
                order(side, ONE, P, p(ONE, v("-.1")), m2)
        );
        OrderBinding deal = DealerHelper.deal(openTempl, m1, closeOrders, m2);

        assertNotNull(deal);
    }

    @Test
    public void testDealFee() throws Exception {
        LimitOrder openTempl = order(side, ONE, P, ONE, m1);
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2")), m2),
                order(side, ONE, P, p(ONE, v("-.1")), m2)
        );
        OrderBinding deal = DealerHelper.deal(openTempl, m1, closeOrders, m2);

        assertNotNull(deal);
    }

    @Test
    public void testCloseAmount() {
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.3")), m2),
                order(side, v(".7"), P, p(ONE, v("-.2")), m2),
                order(side, ONE, P, p(ONE, v("-.1")), m2)
        );
        LimitOrder openTempl = order(side, ONE, P, ONE, m1);
        BigDecimal closeAmount = DealerHelper.getCloseAmount(closeOrders, openTempl, m1);

        LoggerFactory.getLogger(getClass()).info("{}", closeAmount);
        assertTrue(Numbers.eq(closeAmount, v("2.2")));
    }

    @Test
    public void testCloseAmount2() {
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.3")), m2),
                order(side, v(".7"), P, p(ONE, v("-.2")), m2),
                order(side, ONE, P, p(ONE, v("-.1")), m2)
        );
        LimitOrder openTempl = order(side, ONE, P, ONE, m1);
        BigDecimal closeAmount = DealerHelper.getCloseAmount(closeOrders, openTempl, m1);

        log.info("{}", openTempl);
        log.info("{}", closeAmount);
        assertTrue(Numbers.eq(closeAmount, v("2.2")));
    }

    protected BigDecimal p(BigDecimal base, BigDecimal delta) {
        BigDecimal myDelta = side == OrderType.ASK ? delta : delta.negate();
        return base.add(myDelta);
    }

    protected static BigDecimal v(final String s) {
        return new BigDecimal(s);
    }


    IExchangeMonitor m1 = m();
    IExchangeMonitor m2 = m();

    private IExchangeMonitor m() {
        IExchangeMonitor m = mock(IExchangeMonitor.class);
        IWalletMonitor walletMon = mock(IWalletMonitor.class);
        when(m.getWalletMonitor()).thenReturn(walletMon);
        when(m.getMarketMetadata(any())).thenReturn(new BaseMarketMetadata(ONE.movePointLeft(2), 2, ONE.movePointLeft(2)));

        when(walletMon.getWallet(any())).thenReturn(TEN);
        return m;
    }

    static CurrencyPair P = CurrencyPair.BTC_EUR;

    protected static LimitOrder order(OrderType type, BigDecimal tradableAmount, CurrencyPair pair, BigDecimal limitPrice, IExchangeMonitor m) {
        LimitOrder order = new LimitOrder(type, tradableAmount, pair, null, null, limitPrice);
        Orders.updateNetPrice(order, m);
        return order;
    }

}