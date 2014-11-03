package com.hashnot.fx.framework.impl;

import com.hashnot.xchange.event.ITradesMonitor;
import com.hashnot.xchange.event.IUserTradeListener;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.dto.trade.UserTrades;
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
        IUserTradeListener orderListener = mock(IUserTradeListener.class);

        ITradesMonitor tradesMonitor = mock(ITradesMonitor.class);
        TrackingUserTradesMonitor trackingTradesMonitor = new TrackingUserTradesMonitor(tradesMonitor);
        trackingTradesMonitor.addTradeListener(orderListener);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, ONE, BTC_EUR, id, null, ONE);
        trackingTradesMonitor.limitOrderPlaced(order, id);

        UserTrade trade = trade(ASK, ONE, BTC_EUR, ONE, "id", id);
        trackingTradesMonitor.trades(new UserTrades(asList(trade), 0l, Trades.TradeSortType.SortByID));
        verify(orderListener).trade(order, trade, null);
    }

    @Test
    public void testLimitOrdersPlaced() throws Exception {
        IUserTradeListener orderListener = mock(IUserTradeListener.class);

        ITradesMonitor tradesMonitor = mock(ITradesMonitor.class);
        TrackingUserTradesMonitor trackingTradesMonitor = new TrackingUserTradesMonitor(tradesMonitor);
        trackingTradesMonitor.addTradeListener(orderListener);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, TWO, BTC_EUR, id, null, ONE);
        trackingTradesMonitor.limitOrderPlaced(order, id);

        UserTrade t1 = trade(ASK, ONE, BTC_EUR, ONE, "t1", id);
        UserTrade t2 = trade(ASK, ONE, BTC_EUR, ONE, "t2", id);

        trackingTradesMonitor.trades(new UserTrades(asList(t1, t2), 0l, Trades.TradeSortType.SortByID));
        verify(orderListener, times(1)).trade(order, trade(ASK, TWO, BTC_EUR, ONE, "t2", id), null);
    }

    @Test
    public void testLimitOrdersPlaced2() throws Exception {
        IUserTradeListener orderListener = mock(IUserTradeListener.class);

        ITradesMonitor tradesMonitor = mock(ITradesMonitor.class);
        TrackingUserTradesMonitor trackingTradesMonitor = new TrackingUserTradesMonitor(tradesMonitor);
        trackingTradesMonitor.addTradeListener(orderListener);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, TWO, BTC_EUR, id, null, ONE);
        trackingTradesMonitor.limitOrderPlaced(order, id);

        UserTrade t1 = trade(ASK, ONE, BTC_EUR, ONE, "t1", id);
        UserTrade t2 = trade(ASK, ONE, BTC_EUR, ONE, "t2", id);

        LimitOrder one = new LimitOrder(ASK, ONE, BTC_EUR, id, null, ONE);

        trackingTradesMonitor.trades(new UserTrades(asList(t1), 0l, Trades.TradeSortType.SortByID));
        verify(orderListener).trade(order, t1, one);

        trackingTradesMonitor.trades(new UserTrades(asList(t2), 0l, Trades.TradeSortType.SortByID));
        verify(orderListener).trade(order, t2, null);
    }

    private static UserTrade trade(Order.OrderType side, BigDecimal amount, CurrencyPair pair, BigDecimal price, String tradeId, String orderId) {
        return new UserTrade(side, amount, pair, price, null, tradeId, orderId, null, null);
    }
}
