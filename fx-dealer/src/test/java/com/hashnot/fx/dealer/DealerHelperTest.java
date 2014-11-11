package com.hashnot.fx.dealer;

import com.hashnot.xchange.event.IExchangeMonitor;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;

// TODO asserts
@Ignore
public class DealerHelperTest {

    @Test
    public void testAskDeal() throws Exception {
        LimitOrder worst = order(ASK, ONE, P, ONE);
        List<LimitOrder> bestOrders = Arrays.asList(
                order(ASK, v(".5"), P, v(".8")),
                order(ASK, ONE, P, v(".9"))
        );
        DealerHelper.deal(worst, m1, bestOrders, m2);
    }

    @Test
    public void testBidDeal() throws Exception {
        LimitOrder worst = order(BID, ONE, P, ONE);
        List<LimitOrder> bestOrders = Arrays.asList(
                order(BID, v(".5"), P, v("1.1")),
                order(BID, ONE, P, v("1.2"))
        );
        DealerHelper.deal(worst, m1, bestOrders, m2);
    }

    @Test
    public void testAskDealFee() throws Exception {
        LimitOrder worst = order(ASK, ONE, P, ONE);
        List<LimitOrder> bestOrders = Arrays.asList(
                order(ASK, v(".5"), P, v(".8")),
                order(ASK, ONE, P, v(".9"))
        );
        DealerHelper.deal(worst, m1, bestOrders, m2);
    }

    @Test
    public void testBidDealFee() throws Exception {
        LimitOrder worst = order(BID, ONE, P, ONE);
        List<LimitOrder> bestOrders = Arrays.asList(
                order(BID, v(".5"), P, v("1.2")),
                order(BID, ONE, P, v("1.1"))
        );
        DealerHelper.deal(worst, m1, bestOrders, m2);
    }

    protected static BigDecimal v(final String s) {
        return new BigDecimal(s);
    }


    IExchangeMonitor m1;
    IExchangeMonitor m2;

    static CurrencyPair P = CurrencyPair.BTC_EUR;

    protected static LimitOrder order(Order.OrderType type, BigDecimal tradableAmount, CurrencyPair pair, BigDecimal limitPrice) {
        LimitOrder order = new LimitOrder(type, tradableAmount, pair, null, null, limitPrice);
        order.setNetPrice(limitPrice);
        return order;
    }

}