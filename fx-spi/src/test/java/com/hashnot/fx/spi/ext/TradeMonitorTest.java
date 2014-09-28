package com.hashnot.fx.spi.ext;

import com.hashnot.fx.spi.ITradeListener;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Collections;

import static com.xeiam.xchange.currency.CurrencyPair.BTC_EUR;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static java.math.BigDecimal.ONE;
import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class TradeMonitorTest {

    @Test
    public void testLimitOrderPlaced() throws Exception {
        IExchange exchange = mock(IExchange.class);
        ITradeService tradeService = mock(ITradeService.class);

        when(exchange.getPollingTradeService()).thenReturn(tradeService);

        when(tradeService.getTradeHistory()).thenReturn(new Trades(Collections.emptyList(), 0L, Trades.TradeSortType.SortByID));

        ITradeListener orderListener = mock(ITradeListener.class);
        RunnableScheduler scheduler = mock(RunnableScheduler.class);

        TradeMonitor tradeMonitor = new TradeMonitor(exchange, scheduler);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, ONE, BTC_EUR, id, null, ONE);
        tradeMonitor.limitOrderPlaced(order, id);

        tradeMonitor.run();
        Mockito.verify(orderListener, never()).trade(any(), null, any());

        when(tradeService.getTradeHistory()).thenReturn(new Trades(asList(new Trade(ASK, ONE, BTC_EUR, ONE, null, "id", id)), 0l, Trades.TradeSortType.SortByID));

        tradeMonitor.run();
        verify(orderListener).trade(order, null, null);
    }

    @Test
    public void testLimitOrdersPlaced() throws Exception {
        IExchange exchange = mock(IExchange.class);
        ITradeService tradeService = mock(ITradeService.class);

        when(exchange.getPollingTradeService()).thenReturn(tradeService);

        ITradeListener orderListener = mock(ITradeListener.class);
        RunnableScheduler scheduler = mock(RunnableScheduler.class);

        TradeMonitor tradeMonitor = new TradeMonitor(exchange, scheduler);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, new BigDecimal(2), BTC_EUR, id, null, ONE);
        tradeMonitor.limitOrderPlaced(order, id);

        Trade t1 = new Trade(ASK, ONE, BTC_EUR, ONE, null, "t1", id);
        Trade t2 = new Trade(ASK, ONE, BTC_EUR, ONE, null, "t2", id);
        when(tradeService.getTradeHistory()).thenReturn(new Trades(asList(t1, t2), 0l, Trades.TradeSortType.SortByID));

        tradeMonitor.run();
        verify(orderListener).trade(order, null, null);
    }

    @Test
    public void testLimitOrdersPlaced2() throws Exception {
        IExchange exchange = mock(IExchange.class);
        ITradeService tradeService = mock(ITradeService.class);

        when(exchange.getPollingTradeService()).thenReturn(tradeService);

        ITradeListener orderListener = mock(ITradeListener.class);
        RunnableScheduler scheduler = mock(RunnableScheduler.class);

        TradeMonitor tradeMonitor = new TradeMonitor(exchange, scheduler);

        String id = "ID";
        LimitOrder order = new LimitOrder(ASK, new BigDecimal(2), BTC_EUR, id, null, ONE);
        tradeMonitor.limitOrderPlaced(order, id);

        Trade t1 = new Trade(ASK, ONE, BTC_EUR, ONE, null, "t1", id);
        Trade t2 = new Trade(ASK, ONE, BTC_EUR, ONE, null, "t2", id);
        when(tradeService.getTradeHistory())
                .thenReturn(new Trades(asList(t1), 0l, Trades.TradeSortType.SortByID))
                .thenReturn(new Trades(asList(t2), 0l, Trades.TradeSortType.SortByID));

        LimitOrder one = new LimitOrder(ASK, ONE, BTC_EUR, id, null, ONE);

        tradeMonitor.run();
        verify(orderListener).trade(order, null, one);

        tradeMonitor.run();
        verify(orderListener).trade(order, null, null);
    }
}
