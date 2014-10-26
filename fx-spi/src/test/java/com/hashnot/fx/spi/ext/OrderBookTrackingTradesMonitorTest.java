package com.hashnot.fx.spi.ext;

import com.hashnot.fx.OrderBookUpdateEvent;
import com.hashnot.fx.ext.ITradeListener;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
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

    @Test
    public void testBasicChange() {
        CurrencyPair pair = CurrencyPair.BTC_EUR;

        PollingTradeService tradeService = mock(PollingTradeService.class);
        ITradeListener orderListener = mock(ITradeListener.class);

        OrderBookTradeMonitor mon = new OrderBookTradeMonitor(tradeService);

        mon.addTradeListener(orderListener);


        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, pair, null, null, ONE);
        mon.limitOrderPlaced(one, "id");

        List<LimitOrder> asksAfter = asList(one);

        OrderBook stage0 = new OrderBook(null, emptyList(), emptyList());
        OrderBook stage1 = new OrderBook(null, asksAfter, emptyList());

        mon.orderBookChanged(new OrderBookUpdateEvent(null, pair, stage0, stage1));

        mon.orderBookChanged(new OrderBookUpdateEvent(null, pair, stage1, stage0));

        verify(orderListener, times(1)).trade(one, null, null);

    }

    @Ignore
    @Test
    public void testExtraOrder() {
        CurrencyPair pair = CurrencyPair.BTC_EUR;

        PollingTradeService tradeService = mock(PollingTradeService.class);
        ITradeListener orderListener = mock(ITradeListener.class);

        OrderBookTradeMonitor mon = new OrderBookTradeMonitor(tradeService);
        mon.addTradeListener(orderListener);


        OrderBook stage0 = new OrderBook(null, emptyList(), emptyList());

        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, pair, "o1", null, ONE);
        mon.limitOrderPlaced(one, "id");

        OrderBook stage1 = new OrderBook(null, asList(one), emptyList());
        mon.orderBookChanged(new OrderBookUpdateEvent(null, pair, stage0, stage1));

        LimitOrder two = new LimitOrder(Order.OrderType.ASK, TWO, pair, "o2", null, ONE);
        OrderBook stage2 = new OrderBook(null, asList(two), emptyList());
        mon.orderBookChanged(new OrderBookUpdateEvent(null, pair, stage1, stage2));

        mon.orderBookChanged(new OrderBookUpdateEvent(null, pair, stage2, stage1));

        verify(orderListener, times(1)).trade(one, null, one);
    }

    @Test
    public void testTwoMonitored() {
        CurrencyPair pair = CurrencyPair.BTC_EUR;

        PollingTradeService tradeService = mock(PollingTradeService.class);
        ITradeListener orderListener = mock(ITradeListener.class);

        OrderBookTradeMonitor mon = new OrderBookTradeMonitor(tradeService);
        mon.addTradeListener(orderListener);


        OrderBook stage0 = new OrderBook(null, emptyList(), emptyList());

        LimitOrder one = new LimitOrder(Order.OrderType.ASK, ONE, pair, null, null, ONE);
        LimitOrder two = new LimitOrder(Order.OrderType.ASK, TWO, pair, null, null, ONE);
        mon.limitOrderPlaced(two, "id");

        OrderBook stage1 = new OrderBook(null, asList(two), emptyList());
        mon.orderBookChanged(new OrderBookUpdateEvent(null, pair, stage0, stage1));

        OrderBook stage2 = new OrderBook(null, asList(one), emptyList());
        mon.orderBookChanged(new OrderBookUpdateEvent(null, pair, stage1, stage2));
        verify(orderListener).trade(two, null, one);

        mon.orderBookChanged(new OrderBookUpdateEvent(null, pair, stage2, stage0));
        verify(orderListener).trade(two, null, null);
    }
}