package com.hashnot.fx.framework.impl;

import com.hashnot.fx.framework.*;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.market.IOrderBookMonitor;
import com.hashnot.xchange.event.market.ITickerMonitor;
import com.hashnot.xchange.event.market.OrderBookUpdateEvent;
import com.hashnot.xchange.event.market.TickerEvent;
import com.hashnot.xchange.ext.Market;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static org.mockito.Mockito.*;

public class BestOfferMonitorTest {

    private final static CurrencyPair p = CurrencyPair.BTC_EUR;
    Ticker t = new Ticker.Builder().currencyPair(p).ask(ONE).bid(ONE).build();

    static Exchange x = mock(Exchange.class);
    static IExchangeMonitor monitor = mock(IExchangeMonitor.class);

    private static Market m(IOrderBookMonitor obm, ITickerMonitor tm) {
        reset(x, monitor);
        if (obm != null)
            when(monitor.getOrderBookMonitor()).thenReturn(obm);
        if (tm != null)
            when(monitor.getTickerMonitor()).thenReturn(tm);


        return new Market(x, p);
    }

    private static BestOfferEvent boe(MarketSide ms) {
        return new BestOfferEvent(ONE, ms);
    }

    private static OrderBookUpdateEvent obue(Market m) {
        return new OrderBookUpdateEvent(m, new OrderBook(
                null,
                Arrays.asList(new LimitOrder.Builder(ASK, p).limitPrice(ONE).timestamp(null).build()),
                Arrays.asList(new LimitOrder.Builder(BID, p).limitPrice(ONE).timestamp(null).build())
        ));
    }

    private static OrderBookSideUpdateEvent obsue(MarketSide ms) {
        return new OrderBookSideUpdateEvent(ms,
                null,
                Arrays.asList(new LimitOrder.Builder(ASK, p).limitPrice(ONE).timestamp(null).build())
        );
    }

    @Test
    public void testAddOrderBookListener() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IOrderBookSideListener obl = mock(IOrderBookSideListener.class);
        MarketSide ms = new MarketSide(m(obm, tm), ASK);
        BestOfferMonitor mon = new BestOfferMonitor(map(x, monitor));


        mon.addOrderBookSideListener(obl, ms);
        verify(obm).addOrderBookListener(mon, p);


        mon.ticker(new TickerEvent(t, ms.market.exchange));
        verifyZeroInteractions(obl);


        mon.orderBookChanged(obue(m(obm, tm)));
        verify(obl).orderBookSideChanged(obsue(ms));
        verifyNoMoreInteractions(obl);
    }

    @Test
    public void testAddTickerListener() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IBestOfferListener bol = mock(IBestOfferListener.class);
        Market m = m(obm, tm);
        MarketSide ms = new MarketSide(m, ASK);
        BestOfferMonitor mon = new BestOfferMonitor(map(x, monitor));


        mon.addBestOfferListener(bol, ms);
        verify(tm).addTickerListener(mon, p);


        mon.ticker(new TickerEvent(t, m.exchange));
        verify(bol).updateBestOffer(eq(boe(ms)));
        //verify(bol).updateBestOffer(eq(boe(m, BID)));
        verifyNoMoreInteractions(bol);


        mon.orderBookChanged(obue(m));
        verifyNoMoreInteractions(bol);
    }

    @Test
    public void testAddBolAndObl() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IBestOfferListener bol = mock(IBestOfferListener.class);
        IOrderBookSideListener obl = mock(IOrderBookSideListener.class);
        Market m = m(obm, tm);
        MarketSide ms = new MarketSide(m, ASK);
        BestOfferMonitor mon = new BestOfferMonitor(map(x, monitor));


        mon.addBestOfferListener(bol, ms);
        verify(tm).addTickerListener(mon, p);
        verifyNoMoreInteractions(tm);
        verifyZeroInteractions(obm);


        mon.addOrderBookSideListener(obl, ms);
        verify(tm).removeTickerListener(mon, p);
        verifyNoMoreInteractions(tm);
        verify(obm, atLeastOnce()).addOrderBookListener(mon, p);
        //verify(obm).addOrderBookListener(mon, p);
        verifyNoMoreInteractions(obm);


        mon.ticker(new TickerEvent(t, m.exchange));
        verifyZeroInteractions(obl);
        verifyZeroInteractions(bol);


        mon.orderBookChanged(obue(m));
        verify(bol).updateBestOffer(eq(boe(ms)));
        verifyNoMoreInteractions(bol);

        verify(obl).orderBookSideChanged(eq(obsue(ms)));
        verifyZeroInteractions(obl);
    }

    @Test
    public void testAddOblAndBol() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IOrderBookSideListener obsl = mock(IOrderBookSideListener.class);
        IBestOfferListener bol = mock(IBestOfferListener.class);
        Market m = m(obm, tm);
        MarketSide ms = new MarketSide(m, ASK);
        BestOfferMonitor mon = new BestOfferMonitor(map(x, monitor));


        mon.addOrderBookSideListener(obsl, ms);
        verify(obm).addOrderBookListener(mon, p);


        mon.addBestOfferListener(bol, ms);
        verify(obm, atLeastOnce()).addOrderBookListener(mon, p);


        mon.ticker(new TickerEvent(t, m.exchange));
        verifyZeroInteractions(obsl);
        verifyZeroInteractions(bol);


        mon.orderBookChanged(obue(m));
        verify(obsl).orderBookSideChanged(obsue(ms));
        verifyNoMoreInteractions(obsl);

        verify(bol).updateBestOffer(eq(boe(ms)));
        verifyNoMoreInteractions(bol);
    }

    @Test
    public void testAddBolOblRemoveObl() {
        IOrderBookMonitor obm = mock(IOrderBookMonitor.class);
        ITickerMonitor tm = mock(ITickerMonitor.class);
        IBestOfferListener bol = mock(IBestOfferListener.class);
        IOrderBookSideListener obl = mock(IOrderBookSideListener.class);
        Market m = m(obm, tm);
        MarketSide ms = new MarketSide(m, ASK);
        BestOfferMonitor mon = new BestOfferMonitor(map(x, monitor));


        mon.addBestOfferListener(bol, ms);
        verify(tm).addTickerListener(mon, p);
        verifyNoMoreInteractions(tm);
        verifyZeroInteractions(obm);


        mon.addOrderBookSideListener(obl, ms);
        verify(tm).removeTickerListener(mon, p);
        verify(obm, atLeastOnce()).addOrderBookListener(mon, p);
        //verify(obm).addOrderBookListener(mon, p);
        verifyNoMoreInteractions(tm);
        verifyNoMoreInteractions(obm);


        mon.removeOrderBookSideListener(obl, ms);
        verify(tm, atLeastOnce()).addTickerListener(mon, p);
        verifyNoMoreInteractions(tm);


        mon.ticker(new TickerEvent(t, m.exchange));
        verify(bol).updateBestOffer(eq(boe(ms)));
        verifyNoMoreInteractions(bol);
        verifyZeroInteractions(obl);


        mon.orderBookChanged(obue(m));
        verify(bol).updateBestOffer(eq(boe(ms)));
        verifyNoMoreInteractions(bol);

        // mon doesn't call obl
        verifyZeroInteractions(obl);
    }


    private static <K, V> Map<K, V> map(K key, V value) {
        HashMap<K, V> result = new HashMap<>();
        result.put(key, value);
        return result;
    }
}
