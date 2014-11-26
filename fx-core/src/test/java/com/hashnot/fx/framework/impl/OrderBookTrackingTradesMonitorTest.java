package com.hashnot.fx.framework.impl;

import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.framework.OrderBookSideUpdateEvent;
import com.hashnot.xchange.event.trade.IUserTradeListener;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.hashnot.xchange.ext.Market;
import com.hashnot.xchange.ext.trade.OrderEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static java.math.BigDecimal.ONE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.*;

@Ignore
public class OrderBookTrackingTradesMonitorTest {
    private static final BigDecimal TWO = new BigDecimal(2);
    protected static final CurrencyPair P = CurrencyPair.BTC_EUR;
    final private static Exchange x = mock(Exchange.class);
    final private static Market m = new Market(x, P);
    final private static MarketSide ms = new MarketSide(m, Order.OrderType.ASK);

    @Test
    public void testBasicChange() {

        PollingTradeService tradeService = mock(PollingTradeService.class);
        IUserTradeListener orderListener = mock(IUserTradeListener.class);

        OrderBookUserTradeMonitor mon = new OrderBookUserTradeMonitor(tradeService);

        mon.addTradeListener(orderListener);


        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, m.listing, null, null, ONE);
        mon.limitOrderPlaced(new OrderEvent<>("id", one, x));

        List<LimitOrder> stage0 = emptyList();
        List<LimitOrder> stage1 = asList(one);

        mon.orderBookSideChanged(new OrderBookSideUpdateEvent(ms, stage0, stage1));

        mon.orderBookSideChanged(new OrderBookSideUpdateEvent(ms, stage1, stage0));

        verify(orderListener, times(1)).trade(new UserTradeEvent(one, null, null, null));

    }

    @Ignore
    @Test
    public void testExtraOrder() {
        PollingTradeService tradeService = mock(PollingTradeService.class);
        IUserTradeListener orderListener = mock(IUserTradeListener.class);

        OrderBookUserTradeMonitor mon = new OrderBookUserTradeMonitor(tradeService);
        mon.addTradeListener(orderListener);


        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, P, "o1", null, ONE);
        mon.limitOrderPlaced(new OrderEvent<>("id", one, x));

        List<LimitOrder> stage0 = emptyList();
        List<LimitOrder> stage1 = asList(one);
        mon.orderBookSideChanged(new OrderBookSideUpdateEvent(ms, stage0, stage1));

        LimitOrder two = new LimitOrder(Order.OrderType.ASK, TWO, P, "o2", null, ONE);
        List<LimitOrder> stage2 = asList(two);
        mon.orderBookSideChanged(new OrderBookSideUpdateEvent(ms, stage1, stage2));

        mon.orderBookSideChanged(new OrderBookSideUpdateEvent(ms, stage2, stage1));

        verify(orderListener, times(1)).trade(new UserTradeEvent(one, null, one, null));
    }

    @Test
    public void testTwoMonitored() {
        PollingTradeService tradeService = mock(PollingTradeService.class);
        IUserTradeListener orderListener = mock(IUserTradeListener.class);

        OrderBookUserTradeMonitor mon = new OrderBookUserTradeMonitor(tradeService);
        mon.addTradeListener(orderListener);


        List<LimitOrder> stage0 = emptyList();

        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, P, null, null, ONE);
        LimitOrder two = new LimitOrder(Order.OrderType.ASK, TWO, P, null, null, ONE);
        mon.limitOrderPlaced(new OrderEvent<>("id", two, x));

        List<LimitOrder> stage1 = asList(two);
        mon.orderBookSideChanged(new OrderBookSideUpdateEvent(ms, stage0, stage1));

        List<LimitOrder> stage2 = asList(one);
        mon.orderBookSideChanged(new OrderBookSideUpdateEvent(ms, stage1, stage2));
        verify(orderListener).trade(new UserTradeEvent(two, null, one, null));

        mon.orderBookSideChanged(new OrderBookSideUpdateEvent(ms, stage2, stage0));
        verify(orderListener).trade(new UserTradeEvent(two, null, null, null));
    }
}