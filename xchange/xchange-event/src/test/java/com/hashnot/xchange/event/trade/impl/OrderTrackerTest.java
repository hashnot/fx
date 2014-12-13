package com.hashnot.xchange.event.trade.impl;

import com.hashnot.xchange.event.trade.IUserTradeListener;
import com.hashnot.xchange.event.trade.IUserTradesMonitor;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.hashnot.xchange.event.trade.UserTradesEvent;
import com.hashnot.xchange.ext.trade.OrderEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.dto.trade.UserTrades;
import org.junit.Test;

import java.math.BigDecimal;

import static com.hashnot.xchange.ext.util.Numbers.BigDecimal.TWO;
import static com.xeiam.xchange.currency.CurrencyPair.BTC_EUR;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static java.math.BigDecimal.ONE;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.*;

public class OrderTrackerTest {

    private static Exchange source = mock(Exchange.class);

    @Test
    public void testLimitOrderPlaced() throws Exception {
        IUserTradeListener orderListener = mock(IUserTradeListener.class);

        IUserTradesMonitor tradesMonitor = mock(IUserTradesMonitor.class);
        OrderTracker orderTracker = new OrderTracker(tradesMonitor);
        orderTracker.addTradeListener(orderListener);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, ONE, BTC_EUR, id, null, ONE);
        orderTracker.limitOrderPlaced(new OrderEvent<>(id, order, source));

        UserTrade trade = trade(ASK, ONE, BTC_EUR, ONE, "id", id);
        orderTracker.trades(new UserTradesEvent(new UserTrades(asList(trade), 0l, Trades.TradeSortType.SortByID), source));
        verify(orderListener).trade(new UserTradeEvent(order, trade, null, source));
    }

    @Test
    public void testLimitOrdersPlaced() throws Exception {
        IUserTradeListener orderListener = mock(IUserTradeListener.class);

        IUserTradesMonitor tradesMonitor = mock(IUserTradesMonitor.class);
        OrderTracker orderTracker = new OrderTracker(tradesMonitor);
        orderTracker.addTradeListener(orderListener);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, TWO, BTC_EUR, id, null, ONE);
        orderTracker.limitOrderPlaced(new OrderEvent<>(id, order, source));

        String oid1 = "t1";
        String oid2 = "t2";
        UserTrade t1 = trade(ASK, ONE, BTC_EUR, ONE, oid1, id);
        UserTrade t2 = trade(ASK, ONE, BTC_EUR, ONE, oid2, id);

        orderTracker.trades(new UserTradesEvent(new UserTrades(asList(t1, t2), 0l, Trades.TradeSortType.SortByID), source));
        verify(orderListener, times(1)).trade(new UserTradeEvent(order, trade(ASK, ONE, BTC_EUR, ONE, oid1, id), LimitOrder.Builder.from(order).tradableAmount(ONE).build(), source));
        verify(orderListener, times(1)).trade(new UserTradeEvent(order, trade(ASK, ONE, BTC_EUR, ONE, oid2, id), null, source));
    }

    @Test
    public void testLimitOrdersPlaced2() throws Exception {
        IUserTradeListener orderListener = mock(IUserTradeListener.class);

        IUserTradesMonitor tradesMonitor = mock(IUserTradesMonitor.class);
        OrderTracker orderTracker = new OrderTracker(tradesMonitor);
        orderTracker.addTradeListener(orderListener);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, TWO, BTC_EUR, id, null, ONE);
        orderTracker.limitOrderPlaced(new OrderEvent<>(id, order, source));

        UserTrade t1 = trade(ASK, ONE, BTC_EUR, ONE, "t1", id);
        UserTrade t2 = trade(ASK, ONE, BTC_EUR, ONE, "t2", id);

        LimitOrder one = new LimitOrder(ASK, ONE, BTC_EUR, id, null, ONE);

        orderTracker.trades(new UserTradesEvent(new UserTrades(asList(t1), 0l, Trades.TradeSortType.SortByID), source));
        verify(orderListener).trade(new UserTradeEvent(order, t1, one, source));

        orderTracker.trades(new UserTradesEvent(new UserTrades(asList(t2), 0l, Trades.TradeSortType.SortByID), source));
        verify(orderListener).trade(new UserTradeEvent(order, t2, null, source));
    }

    private static UserTrade trade(Order.OrderType side, BigDecimal amount, CurrencyPair pair, BigDecimal price, String tradeId, String orderId) {
        return new UserTrade(side, amount, pair, price, null, tradeId, orderId, null, null);
    }
}
