package com.hashnot.fx.spi.ext;

import com.hashnot.fx.OrderBookUpdateEvent;
import com.hashnot.fx.spi.IOrderListener;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.*;

@Ignore
public class OrderBookTradeMonitorTest {
    private static final BigDecimal TWO = new BigDecimal(2);

    @Test
    public void testBasicChange() {
        CurrencyPair pair = CurrencyPair.BTC_EUR;

        PollingTradeService tradeService = mock(PollingTradeService.class);
        IOrderListener orderListener = mock(IOrderListener.class);

        OrderBookTradeMonitor mon = new OrderBookTradeMonitor(tradeService);

        mon.addOrderListener(orderListener);


        LimitOrder one = new LimitOrder(Order.OrderType.ASK, BigDecimal.ONE, pair, null, null, BigDecimal.ONE);
        mon.afterPlaceLimitOrder(one);

        List<LimitOrder> asksAfter = asList(one);

        OrderBook stage0 = new OrderBook(null, emptyList(), emptyList());
        OrderBook stage1 = new OrderBook(null, asksAfter, emptyList());

        mon.changed(new OrderBookUpdateEvent(pair, stage0, stage1));

        mon.changed(new OrderBookUpdateEvent(pair, stage1, stage0));

        verify(orderListener, times(1)).trade(one, null);

    }

    @Test
    public void testExtraOrder() {
        CurrencyPair pair = CurrencyPair.BTC_EUR;

        PollingTradeService tradeService = mock(PollingTradeService.class);
        IOrderListener orderListener = mock(IOrderListener.class);

        OrderBookTradeMonitor mon = new OrderBookTradeMonitor(tradeService);
        mon.addOrderListener(orderListener);


        OrderBook stage0 = new OrderBook(null, emptyList(), emptyList());

        LimitOrder one = new LimitOrder(Order.OrderType.ASK, BigDecimal.ONE, pair, null, null, BigDecimal.ONE);
        mon.afterPlaceLimitOrder(one);

        OrderBook stage1 = new OrderBook(null, asList(one), emptyList());
        mon.changed(new OrderBookUpdateEvent(pair, stage0, stage1));

        LimitOrder two = new LimitOrder(Order.OrderType.ASK, TWO, pair, null, null, BigDecimal.ONE);
        OrderBook stage2 = new OrderBook(null, asList(two), emptyList());
        mon.changed(new OrderBookUpdateEvent(pair, stage1, stage2));

        mon.changed(new OrderBookUpdateEvent(pair, stage2, stage1));

        verify(orderListener, times(1)).trade(one, one);
    }

    @Test
    public void testTwoMonitored() {
        CurrencyPair pair = CurrencyPair.BTC_EUR;

        PollingTradeService tradeService = mock(PollingTradeService.class);
        IOrderListener orderListener = mock(IOrderListener.class);

        OrderBookTradeMonitor mon = new OrderBookTradeMonitor(tradeService);
        mon.addOrderListener(orderListener);


        OrderBook stage0 = new OrderBook(null, emptyList(), emptyList());

        LimitOrder one = new LimitOrder(Order.OrderType.ASK, BigDecimal.ONE, pair, null, null, BigDecimal.ONE);
        LimitOrder two = new LimitOrder(Order.OrderType.ASK, TWO, pair, null, null, BigDecimal.ONE);
        mon.afterPlaceLimitOrder(two);

        OrderBook stage1 = new OrderBook(null, asList(two), emptyList());
        mon.changed(new OrderBookUpdateEvent(pair, stage0, stage1));

        OrderBook stage2 = new OrderBook(null, asList(one), emptyList());
        mon.changed(new OrderBookUpdateEvent(pair, stage1, stage2));

        mon.changed(new OrderBookUpdateEvent(pair, stage2, stage0));

        verify(orderListener).trade(two, one);
        verify(orderListener).trade(two, null);

    }
}