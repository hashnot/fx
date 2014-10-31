package com.hashnot.fx.spi.ext;

import com.hashnot.fx.ext.ITradeListener;
import com.hashnot.fx.ext.ITradesMonitor;
import com.hashnot.fx.ext.impl.TrackingTradesMonitor;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.math.BigDecimal;

import static com.xeiam.xchange.currency.CurrencyPair.BTC_EUR;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static java.math.BigDecimal.ONE;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.*;

public class TrackingTradesMonitorTest {

    private static final BigDecimal TWO = new BigDecimal(2);

    @Test
    public void testLimitOrderPlaced() throws Exception {
        ITradeListener orderListener = mock(ITradeListener.class);

        ITradesMonitor tradesMonitor = mock(ITradesMonitor.class);
        TrackingTradesMonitor trackingTradesMonitor = new TrackingTradesMonitor(tradesMonitor);
        trackingTradesMonitor.addTradeListener(orderListener);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, ONE, BTC_EUR, id, null, ONE);
        trackingTradesMonitor.limitOrderPlaced(order, id);

        Trade trade = new Trade(ASK, ONE, BTC_EUR, ONE, null, "id");
        trackingTradesMonitor.trades(new Trades(asList(trade), 0l, Trades.TradeSortType.SortByID));
        verify(orderListener).trade(order, trade, null);
    }

    @Test
    public void testLimitOrdersPlaced() throws Exception {
        ITradeListener orderListener = mock(ITradeListener.class);

        ITradesMonitor tradesMonitor = mock(ITradesMonitor.class);
        TrackingTradesMonitor trackingTradesMonitor = new TrackingTradesMonitor(tradesMonitor);
        trackingTradesMonitor.addTradeListener(orderListener);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, TWO, BTC_EUR, id, null, ONE);
        trackingTradesMonitor.limitOrderPlaced(order, id);

        Trade t1 = new Trade(ASK, ONE, BTC_EUR, ONE, null, "t1");
        Trade t2 = new Trade(ASK, ONE, BTC_EUR, ONE, null, "t2");

        trackingTradesMonitor.trades(new Trades(asList(t1, t2), 0l, Trades.TradeSortType.SortByID));
        verify(orderListener, times(1)).trade(order, new Trade(ASK, TWO, BTC_EUR, ONE, null, "t2"), null);
    }

    @Test
    public void testLimitOrdersPlaced2() throws Exception {
        ITradeListener orderListener = mock(ITradeListener.class);

        ITradesMonitor tradesMonitor = mock(ITradesMonitor.class);
        TrackingTradesMonitor trackingTradesMonitor = new TrackingTradesMonitor(tradesMonitor);
        trackingTradesMonitor.addTradeListener(orderListener);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, TWO, BTC_EUR, id, null, ONE);
        trackingTradesMonitor.limitOrderPlaced(order, id);

        Trade t1 = new Trade(ASK, ONE, BTC_EUR, ONE, null, "t1");
        Trade t2 = new Trade(ASK, ONE, BTC_EUR, ONE, null, "t2");

        LimitOrder one = new LimitOrder(ASK, ONE, BTC_EUR, id, null, ONE);

        trackingTradesMonitor.trades(new Trades(asList(t1), 0l, Trades.TradeSortType.SortByID));
        verify(orderListener).trade(order, t1, one);

        trackingTradesMonitor.trades(new Trades(asList(t2), 0l, Trades.TradeSortType.SortByID));
        verify(orderListener).trade(order, t2, null);
    }
}
