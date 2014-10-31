package com.hashnot.fx.ext.impl;

import com.hashnot.fx.ext.OrderBookUpdateEvent;
import com.hashnot.fx.ext.*;
import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.util.Arrays;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static org.mockito.Mockito.*;

public class BestOfferMonitorTest {

    private final static CurrencyPair p = CurrencyPair.BTC_EUR;
    Ticker t = new Ticker.Builder().currencyPair(p).ask(ONE).bid(ONE).build();

    private static Market m(IOrderBookMonitor obm, ITickerMonitor tm) {
        IExchange x = mock(IExchange.class);
        if (obm != null)
            when(x.getOrderBookMonitor()).thenReturn(obm);
        if (tm != null)
            when(x.getTickerMonitor()).thenReturn(tm);


        return new Market(x, p);
    }

    private static BestOfferEvent boe(Market m, Order.OrderType side){
        return new BestOfferEvent(ONE, side, m);
    }

    private static OrderBookUpdateEvent obue(Market m){
        return new OrderBookUpdateEvent(m, null, new OrderBook(
                    null,
                    Arrays.asList(new LimitOrder.Builder(ASK, p).limitPrice(ONE).build()),
                    Arrays.asList(new LimitOrder.Builder(BID, p).limitPrice(ONE).build())
            ));
    }

    @Test
    public void testAddOrderBookListener() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IOrderBookListener obl = mock(IOrderBookListener.class);
        Market m = m(obm, tm);
        BestOfferMonitor mon = new BestOfferMonitor();


        mon.addOrderBookListener(obl, m);
        verify(obm).addOrderBookListener(obl, m);


        mon.ticker(t, m.exchange);
        verifyZeroInteractions(obl);


        mon.orderBookChanged(obue(m));
        verifyZeroInteractions(obl);
    }

    @Test
    public void testAddTickerListener() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IBestOfferListener bol = mock(IBestOfferListener.class);
        Market m = m(obm, tm);
        BestOfferMonitor mon = new BestOfferMonitor();


        mon.addBestOfferListener(bol, m);
        verify(tm).addTickerListener(mon, m);


        mon.ticker(t, m.exchange);
        verify(bol).updateBestOffer(eq(boe(m, ASK)));
        verify(bol).updateBestOffer(eq(boe(m, BID)));
        verifyNoMoreInteractions(bol);


        mon.orderBookChanged(obue(m));
        verifyNoMoreInteractions(bol);
    }

    @Test
    public void testAddBolAndObl() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IBestOfferListener bol = mock(IBestOfferListener.class);
        IOrderBookListener obl = mock(IOrderBookListener.class);
        Market m = m(obm, tm);
        BestOfferMonitor mon = new BestOfferMonitor();


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


        mon.ticker(t, m.exchange);
        verifyZeroInteractions(obl);
        verifyZeroInteractions(bol);


        mon.orderBookChanged(obue(m));
        verify(bol).updateBestOffer(eq(boe(m, ASK)));
        verify(bol).updateBestOffer(eq(boe(m, BID)));
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
        Market m = m(obm, tm);
        BestOfferMonitor mon = new BestOfferMonitor();


        mon.addOrderBookListener(obl, m);
        verify(obm).addOrderBookListener(obl, m);


        mon.addBestOfferListener(bol, m);
        verify(obm).addOrderBookListener(mon, m);
        verifyNoMoreInteractions(obm);
        verifyZeroInteractions(tm);


        mon.ticker(t, m.exchange);
        verifyZeroInteractions(obl);
        verifyZeroInteractions(bol);


        mon.orderBookChanged(obue(m));
        verify(bol).updateBestOffer(eq(boe(m, ASK)));
        verify(bol).updateBestOffer(eq(boe(m, BID)));
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
        Market m = m(obm, tm);
        BestOfferMonitor mon = new BestOfferMonitor();


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


        mon.ticker(t, m.exchange);
        verify(bol).updateBestOffer(eq(boe(m, ASK)));
        verify(bol).updateBestOffer(eq(boe(m, BID)));
        verifyNoMoreInteractions(bol);
        verifyZeroInteractions(obl);


        mon.orderBookChanged(obue(m));
        verify(bol).updateBestOffer(eq(boe(m, ASK)));
        verify(bol).updateBestOffer(eq(boe(m, BID)));
        verifyNoMoreInteractions(bol);

        // mon doesn't call obl
        verifyZeroInteractions(obl);
    }


}
