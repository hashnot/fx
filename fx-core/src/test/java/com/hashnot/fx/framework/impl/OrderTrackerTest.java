package com.hashnot.fx.framework.impl;

import com.hashnot.fx.framework.IUserTradeListener;
import com.hashnot.fx.framework.OrderEvent;
import com.hashnot.fx.framework.UserTradeEvent;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.IUserTradesMonitor;
import com.hashnot.xchange.event.UserTradesEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.dto.trade.UserTrades;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static com.xeiam.xchange.currency.CurrencyPair.BTC_EUR;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static java.math.BigDecimal.ONE;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.*;

public class OrderTrackerTest {

    private static final BigDecimal TWO = new BigDecimal(2);
    private static Exchange source = mock(Exchange.class);

    private static Map<Exchange, IExchangeMonitor> monitors(IUserTradesMonitor userTradesMonitor) {
        HashMap<Exchange, IExchangeMonitor> result = new HashMap<>();
        IExchangeMonitor monitor = mock(IExchangeMonitor.class);
        when(monitor.getUserTradesMonitor()).thenReturn(userTradesMonitor);
        result.put(source, monitor);
        return result;
    }

    @Test
    public void testLimitOrderPlaced() throws Exception {
        IUserTradeListener orderListener = mock(IUserTradeListener.class);

        IUserTradesMonitor tradesMonitor = mock(IUserTradesMonitor.class);
        OrderTracker trackingTradesMonitor = new OrderTracker(monitors(tradesMonitor));
        trackingTradesMonitor.addTradeListener(orderListener, source);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, ONE, BTC_EUR, id, null, ONE);
        trackingTradesMonitor.limitOrderPlaced(new OrderEvent(id, order, source));

        UserTrade trade = trade(ASK, ONE, BTC_EUR, ONE, "id", id);
        trackingTradesMonitor.trades(new UserTradesEvent(new UserTrades(asList(trade), 0l, Trades.TradeSortType.SortByID), source));
        verify(orderListener).trade(new UserTradeEvent(order, trade, null, source));
    }

    @Test
    public void testLimitOrdersPlaced() throws Exception {
        IUserTradeListener orderListener = mock(IUserTradeListener.class);

        IUserTradesMonitor tradesMonitor = mock(IUserTradesMonitor.class);
        OrderTracker trackingTradesMonitor = new OrderTracker(monitors(tradesMonitor));
        trackingTradesMonitor.addTradeListener(orderListener, source);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, TWO, BTC_EUR, id, null, ONE);
        trackingTradesMonitor.limitOrderPlaced(new OrderEvent(id, order, source));

        UserTrade t1 = trade(ASK, ONE, BTC_EUR, ONE, "t1", id);
        UserTrade t2 = trade(ASK, ONE, BTC_EUR, ONE, "t2", id);

        trackingTradesMonitor.trades(new UserTradesEvent(new UserTrades(asList(t1, t2), 0l, Trades.TradeSortType.SortByID), source));
        verify(orderListener, times(1)).trade(new UserTradeEvent(order, trade(ASK, TWO, BTC_EUR, ONE, "t2", id), null, source));
    }

    @Test
    public void testLimitOrdersPlaced2() throws Exception {
        IUserTradeListener orderListener = mock(IUserTradeListener.class);

        IUserTradesMonitor tradesMonitor = mock(IUserTradesMonitor.class);
        OrderTracker trackingTradesMonitor = new OrderTracker(monitors(tradesMonitor));
        trackingTradesMonitor.addTradeListener(orderListener, source);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, TWO, BTC_EUR, id, null, ONE);
        trackingTradesMonitor.limitOrderPlaced(new OrderEvent(id, order, source));

        UserTrade t1 = trade(ASK, ONE, BTC_EUR, ONE, "t1", id);
        UserTrade t2 = trade(ASK, ONE, BTC_EUR, ONE, "t2", id);

        LimitOrder one = new LimitOrder(ASK, ONE, BTC_EUR, id, null, ONE);

        trackingTradesMonitor.trades(new UserTradesEvent(new UserTrades(asList(t1), 0l, Trades.TradeSortType.SortByID), source));
        verify(orderListener).trade(new UserTradeEvent(order, t1, one, source));

        trackingTradesMonitor.trades(new UserTradesEvent(new UserTrades(asList(t2), 0l, Trades.TradeSortType.SortByID), source));
        verify(orderListener).trade(new UserTradeEvent(order, t2, null, source));
    }

    private static UserTrade trade(Order.OrderType side, BigDecimal amount, CurrencyPair pair, BigDecimal price, String tradeId, String orderId) {
        return new UserTrade(side, amount, pair, price, null, tradeId, orderId, null, null);
    }
}
