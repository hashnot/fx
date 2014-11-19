package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.*;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.IWalletMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.BaseMarketMetadata;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hashnot.xchange.ext.util.Numbers.BigDecimal._ONE;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.*;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class DealerTest {
    protected static final CurrencyPair p = CurrencyPair.BTC_EUR;
    protected static final int SCALE = 5;

    private static SimpleOrderCloseStrategy orderCloseStrategy = new SimpleOrderCloseStrategy();
    private static SimpleOrderOpenStrategy orderOpenStrategy = new SimpleOrderOpenStrategy();

    private Order.OrderType side;

    public DealerTest(Order.OrderType side) {
        this.side = side;
    }


    @Parameterized.Parameters
    public static Iterable<Object[]> getParams() {
        return asList(new Object[]{ASK}, new Object[]{BID});
    }

    /**
     * register an exchange, it should appear as both open and close
     */
    @Test
    public void testUpdateBestOffer() throws Exception {
        Exchange closeExchange = mock(Exchange.class);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange);
        Map<Exchange, IExchangeMonitor> monitors = map(closeExchange, closeMonitor);

        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);

        DealerData data = new DealerData();

        Dealer dealer = new Dealer(orderBookSideMonitor, new OrderManager(orderTracker, orderCloseStrategy), monitors, side, p, orderOpenStrategy, data);

        BigDecimal closePrice = new BigDecimal(2);


        // step 1: register first exchange - close
        MarketSide closeSide = new MarketSide(closeExchange, p, side);
        dealer.updateBestOffer(new BestOfferEvent(closePrice, closeSide));

        verify(orderTracker, never()).addTradeListener(any(), any());
        verifyZeroInteractions(orderBookSideMonitor);
        assertEquals(new DealerData(closeExchange, closeExchange, map(closeExchange, closePrice)), data);
    }

    /**
     * register two exchanges in proper order (close, open) - doesn't reregisters order book listener
     */
    @Test
    public void testUpdateBestOfferBoth() throws Exception {
        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);

        Exchange openExchange = mock(Exchange.class);
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange);

        Exchange closeExchange = mock(Exchange.class);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange);
        Map<Exchange, IExchangeMonitor> monitors = map(openExchange, openMonitor, closeExchange, closeMonitor);

        BigDecimal closePrice = new BigDecimal(2);
        DealerData data = data(closeExchange, closePrice);

        Dealer dealer = new Dealer(orderBookSideMonitor, new OrderManager(orderTracker, orderCloseStrategy), monitors, side, p, orderOpenStrategy, data);


        // step 2: register second exchange - open
        MarketSide openSide = new MarketSide(openExchange, p, side);

        BigDecimal openPrice = closePrice.add(side == ASK ? ONE : _ONE);
        dealer.updateBestOffer(new BestOfferEvent(openPrice, openSide));

        verify(orderTracker, never()).addTradeListener(any(), any());

        verify(orderBookSideMonitor).addOrderBookSideListener(eq(dealer), eq(new MarketSide(closeExchange, p, side)));
        verifyNoMoreInteractions(orderBookSideMonitor);
        assertEquals(new DealerData(openExchange, closeExchange, map(openExchange, openPrice, closeExchange, closePrice)), data);
    }

    @Test
    public void testUpdateBestOfferBothReverse() throws Exception {
        Map<Exchange, IExchangeMonitor> monitors = new HashMap<>();
        Exchange openExchange = mock(Exchange.class);
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange);
        monitors.put(openExchange, openMonitor);

        Exchange closeExchange = mock(Exchange.class);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange);
        monitors.put(closeExchange, closeMonitor);

        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);


        // step 1: register first exchange - open
        BigDecimal closePrice = new BigDecimal(2);
        BigDecimal openPrice = closePrice.add(side == ASK ? ONE : _ONE);
        DealerData data = data(openExchange, openPrice);
        Dealer dealer = new Dealer(orderBookSideMonitor, new OrderManager(orderTracker, orderCloseStrategy), monitors, side, p, orderOpenStrategy, data);


        // step 2: register first exchange - close
        MarketSide closeSide = new MarketSide(closeExchange, p, side);
        dealer.updateBestOffer(new BestOfferEvent(closePrice, closeSide));

        verify(orderTracker, never()).addTradeListener(any(), any());
        verify(orderBookSideMonitor).addOrderBookSideListener(eq(dealer), eq(closeSide));
        assertEquals(new DealerData(openExchange, closeExchange, map(openExchange, openPrice, closeExchange, closePrice)), data);
    }

    @Test
    public void testOrderBookSideChanged() throws Exception {
        ITradeService tradeService = mock(ITradeService.class);

        Map<Exchange, IExchangeMonitor> monitors = new HashMap<>();
        Exchange openExchange = mock(Exchange.class);
        when(openExchange.getPollingTradeService()).thenReturn(tradeService);

        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange);
        monitors.put(openExchange, openMonitor);

        Exchange closeExchange = mock(Exchange.class);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange);
        monitors.put(closeExchange, closeMonitor);

        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);
        OrderManager orderManager = spy(new OrderManager(orderTracker,orderCloseStrategy));

        BigDecimal openPrice = new BigDecimal(2);
        BigDecimal priceDiff = ONE;
        if (ASK == side)
            priceDiff = priceDiff.negate();
        BigDecimal closePrice = openPrice.add(priceDiff);


        DealerData data = data(openExchange, openPrice, closeExchange, closePrice);
        Dealer dealer = new Dealer(orderBookSideMonitor, orderManager, monitors, side, p, orderOpenStrategy, data);


        LimitOrder openOrder = new LimitOrder(side, ONE, p, null, null, openPrice);
        openOrder.setNetPrice(openPrice);
        MarketSide closeSide = new MarketSide(closeExchange, p, side);

        LimitOrder closeOrder = from(openOrder).limitPrice(closePrice).build();
        closeOrder.setNetPrice(closePrice);
        List<LimitOrder> closingOrders = asList((closeOrder));
        dealer.orderBookSideChanged(new OrderBookSideUpdateEvent(closeSide, emptyList(), closingOrders));


        BigDecimal diff = ONE.movePointLeft(SCALE);
        if (ASK == side) diff = diff.negate();
        LimitOrder resultingOpenOrder = from(openOrder).limitPrice(openPrice.add(diff)).build();

        // this is highly tuned, mostly because of netPrice inconsistencies
        verify(orderManager).update(argThat(new OrderUpdateEventMatcher(new OrderBinding(openExchange, closeExchange, resultingOpenOrder, closingOrders))));


        // send order book update again, expect no changes
        dealer.orderBookSideChanged(new OrderBookSideUpdateEvent(closeSide, emptyList(), closingOrders));
        verifyZeroInteractions(orderBookSideMonitor);

    }

    protected DealerData data(Exchange exchange, BigDecimal price) {
        return new DealerData(exchange, exchange, map(exchange, price));
    }

    protected DealerData data(Exchange openExchange, BigDecimal openPrice, Exchange closeExchange, BigDecimal closePrice) {
        HashMap<Exchange, BigDecimal> result = new HashMap<>();
        result.put(openExchange, openPrice);
        result.put(closeExchange, closePrice);
        return new DealerData(openExchange, closeExchange, result);
    }

    private static <K, V> Map<K, V> map(K key, V value) {
        HashMap<K, V> result = new HashMap<>();
        result.put(key, value);
        return result;
    }

    private static <K, V> Map<K, V> map(K key1, V value1, K key2, V value2) {
        HashMap<K, V> result = new HashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }

    private static IExchangeMonitor getExchangeMonitor(Exchange x) {
        IExchangeMonitor monitor = mock(IExchangeMonitor.class);

        IWalletMonitor walletMonitor = mock(IWalletMonitor.class);
        when(walletMonitor.getWallet(any())).thenReturn(TEN);

        when(monitor.getWalletMonitor()).thenReturn(walletMonitor);
        when(monitor.getMarketMetadata(p)).thenReturn(new BaseMarketMetadata(ONE.movePointLeft(SCALE), SCALE, ZERO));
        when(monitor.getExchange()).thenReturn(x);
        return monitor;
    }

    private static class OrderUpdateEventMatcher extends TypeSafeMatcher<OrderBinding> {
        private OrderBinding a;

        public OrderUpdateEventMatcher(OrderBinding evt) {
            a = evt;
        }

        @Override
        protected boolean matchesSafely(OrderBinding b) {
            return a.closeExchange == b.closeExchange
                    && a.closingOrders.equals(b.closingOrders)
                    && a.openedOrder.equals(b.openedOrder)
                    && a.openExchange == b.openExchange
                    ;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(a);
        }
    }
}