package com.hashnot.fx.ext.impl;

import com.hashnot.fx.OrderBookUpdateEvent;
import com.hashnot.fx.ext.*;
import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.util.Arrays;

import static java.math.BigDecimal.ONE;
import static org.mockito.Mockito.*;

public class BestOfferMonitorTest {
    IExchange x = mock(IExchange.class);
    private final CurrencyPair p = CurrencyPair.BTC_EUR;
    Market m = new Market(x, p);
    Ticker t = new Ticker.TickerBuilder().withCurrencyPair(p).withAsk(ONE).withBid(ONE).build();
    OrderBookUpdateEvent b = new OrderBookUpdateEvent(x, p, null, new OrderBook(
            null,
            Arrays.asList(new LimitOrder.Builder(Order.OrderType.ASK, p).setLimitPrice(ONE).build()),
            Arrays.asList(new LimitOrder.Builder(Order.OrderType.BID, p).setLimitPrice(ONE).build())
    ));
    private BestOfferEvent askEvt = new BestOfferEvent(ONE, Order.OrderType.ASK, m);
    private BestOfferEvent bidEvt = new BestOfferEvent(ONE, Order.OrderType.BID, m);

    @Test
    public void testAddOrderBookListener() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IOrderBookListener obl = mock(IOrderBookListener.class);

        BestOfferMonitor mon = new BestOfferMonitor(obm, tm);


        mon.addOrderBookListener(obl, m);
        verify(obm).addOrderBookListener(obl, m);


        mon.ticker(t, x);
        verifyZeroInteractions(obl);


        mon.orderBookChanged(b);
        verifyZeroInteractions(obl);
    }

    @Test
    public void testAddTickerListener() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IBestOfferListener bol = mock(IBestOfferListener.class);
        BestOfferMonitor mon = new BestOfferMonitor(obm, tm);


        mon.addBestOfferListener(bol, m);
        verify(tm).addTickerListener(mon, m);


        mon.ticker(t, x);
        verify(bol).updateBestOffer(eq(askEvt));
        verify(bol).updateBestOffer(eq(bidEvt));
        verifyNoMoreInteractions(bol);


        mon.orderBookChanged(b);
        verifyNoMoreInteractions(bol);
    }

    @Test
    public void testAddBolAndObl() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IBestOfferListener bol = mock(IBestOfferListener.class);
        IOrderBookListener obl = mock(IOrderBookListener.class);
        BestOfferMonitor mon = new BestOfferMonitor(obm, tm);


        mon.addBestOfferListener(bol, m);
        verify(tm).addTickerListener(mon, m);
        verifyNoMoreInteractions(tm);
        verifyZeroInteractions(obm);


        mon.addOrderBookListener(obl, m);
        verify(tm).removeTickerListener(mon, m);
        verifyNoMoreInteractions(tm);
        verify(obm).addOrderBookListener(mon, m);
        verify(obm).addOrderBookListener(obl, m);
        verifyNoMoreInteractions(obm);


        mon.ticker(t, x);
        verifyZeroInteractions(obl);
        verifyZeroInteractions(bol);


        mon.orderBookChanged(b);
        verify(bol).updateBestOffer(eq(askEvt));
        verify(bol).updateBestOffer(eq(bidEvt));
        verifyNoMoreInteractions(bol);

        // mon doesn't call obl
        verifyZeroInteractions(obl);
    }

    @Test
    public void testAddOblAndBol() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IOrderBookListener obl = mock(IOrderBookListener.class);
        IBestOfferListener bol = mock(IBestOfferListener.class);
        BestOfferMonitor mon = new BestOfferMonitor(obm, tm);


        mon.addOrderBookListener(obl, m);
        verify(obm).addOrderBookListener(obl, m);


        mon.addBestOfferListener(bol, m);
        verify(obm).addOrderBookListener(mon, m);
        verifyNoMoreInteractions(obm);
        verifyZeroInteractions(tm);


        mon.ticker(t, x);
        verifyZeroInteractions(obl);
        verifyZeroInteractions(bol);


        mon.orderBookChanged(b);
        verify(bol).updateBestOffer(eq(askEvt));
        verify(bol).updateBestOffer(eq(bidEvt));
        verifyNoMoreInteractions(bol);

        // mon doesn't call obl
        verifyZeroInteractions(obl);
    }

    @Test
    public void testAddBolOblRemoveObl() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IBestOfferListener bol = mock(IBestOfferListener.class);
        IOrderBookListener obl = mock(IOrderBookListener.class);
        BestOfferMonitor mon = new BestOfferMonitor(obm, tm);


        mon.addBestOfferListener(bol, m);
        verify(tm).addTickerListener(mon, m);
        verifyNoMoreInteractions(tm);
        verifyZeroInteractions(obm);


        mon.addOrderBookListener(obl, m);
        verify(tm).removeTickerListener(mon, m);
        verify(obm).addOrderBookListener(mon, m);
        verify(obm).addOrderBookListener(obl, m);
        verifyNoMoreInteractions(tm);
        verifyNoMoreInteractions(obm);


        mon.removeOrderBookListener(obl, m);
        verify(obm).removeOrderBookListener(obl, m);
        verify(obm).removeOrderBookListener(mon, m);
        verify(tm, times(2)).addTickerListener(mon, m);
        verifyNoMoreInteractions(tm);
        verifyNoMoreInteractions(obm);


        mon.ticker(t, x);
        verify(bol).updateBestOffer(eq(askEvt));
        verify(bol).updateBestOffer(eq(bidEvt));
        verifyNoMoreInteractions(bol);
        verifyZeroInteractions(obl);


        mon.orderBookChanged(b);
        verify(bol).updateBestOffer(eq(askEvt));
        verify(bol).updateBestOffer(eq(bidEvt));
        verifyNoMoreInteractions(bol);

        // mon doesn't call obl
        verifyZeroInteractions(obl);
    }


}
