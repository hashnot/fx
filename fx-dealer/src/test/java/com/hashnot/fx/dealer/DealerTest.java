package com.hashnot.fx.dealer;

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
import java.util.Map;

import static com.hashnot.xchange.ext.util.Numbers.BigDecimal._ONE;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.*;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
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

    @Test
    public void testUpdateBestOffer() throws Exception {
        Map<Exchange, IExchangeMonitor> monitors = new HashMap<>();
        Exchange openExchange = mock(Exchange.class);
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange);
        monitors.put(openExchange, openMonitor);

        Exchange closeExchange = mock(Exchange.class);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange);
        monitors.put(closeExchange, closeMonitor);

        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);

        Dealer dealer = new Dealer(orderBookSideMonitor, orderTracker, monitors, side, p, orderOpenStrategy, orderCloseStrategy);

        BigDecimal closePrice = new BigDecimal(2);


        // step 1: register first exchange - close
        MarketSide closeSide = new MarketSide(closeExchange, p, side);
        dealer.updateBestOffer(new BestOfferEvent(closePrice, closeSide));

        verify(orderTracker, never()).addTradeListener(any(), any());
        verifyZeroInteractions(orderBookSideMonitor);


        // step 2: register second exchange - open
        MarketSide openSide = new MarketSide(openExchange, p, side);

        BigDecimal openPrice = closePrice.add(side == ASK ? ONE : _ONE);
        dealer.updateBestOffer(new BestOfferEvent(openPrice, openSide));

        verify(orderTracker, never()).addTradeListener(any(), any());

        verify(orderBookSideMonitor).addOrderBookSideListener(eq(dealer), eq(closeSide));
        verifyNoMoreInteractions(orderBookSideMonitor);
    }

    @Test
    public void testUpdateBestOfferReverse() throws Exception {
        Map<Exchange, IExchangeMonitor> monitors = new HashMap<>();
        Exchange openExchange = mock(Exchange.class);
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange);
        monitors.put(openExchange, openMonitor);

        Exchange closeExchange = mock(Exchange.class);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange);
        monitors.put(closeExchange, closeMonitor);

        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);

        Dealer dealer = new Dealer(orderBookSideMonitor, orderTracker, monitors, side, p, orderOpenStrategy, orderCloseStrategy);

        BigDecimal closePrice = new BigDecimal(2);


        // step 1: register second exchange - open
        MarketSide openSide = new MarketSide(openExchange, p, side);

        BigDecimal openPrice = closePrice.add(side == ASK ? ONE : _ONE);
        dealer.updateBestOffer(new BestOfferEvent(openPrice, openSide));

        verifyZeroInteractions(orderTracker);
        verifyZeroInteractions(orderBookSideMonitor);


        // step 2: register first exchange - close
        MarketSide closeSide = new MarketSide(closeExchange, p, side);
        dealer.updateBestOffer(new BestOfferEvent(closePrice, closeSide));


        verify(orderTracker, never()).addTradeListener(any(), any());
        verify(orderBookSideMonitor).addOrderBookSideListener(eq(dealer), eq(closeSide));
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

        BigDecimal openPrice = new BigDecimal(2);
        BigDecimal priceDiff = ONE;
        if (ASK == side)
            priceDiff = priceDiff.negate();
        BigDecimal closePrice = openPrice.add(priceDiff);


        Dealer dealer = new Dealer(orderBookSideMonitor, orderTracker, monitors, side, p, orderOpenStrategy, orderCloseStrategy);

        MarketSide closeSide = new MarketSide(closeExchange, p, side);
        dealer.updateBestOffer(new BestOfferEvent(closePrice, closeSide));


        // step 2: register second exchange - open
        MarketSide openSide = new MarketSide(openExchange, p, side);
        dealer.updateBestOffer(new BestOfferEvent(openPrice, openSide));

        verify(orderBookSideMonitor).addOrderBookSideListener(any(), any());
        verify(orderBookSideMonitor, never()).removeOrderBookSideListener(any(), any());

        // step 3: send order book
        LimitOrder openOrder = new LimitOrder(side, ONE, p, null, null, openPrice);
        openOrder.setNetPrice(openPrice);


        LimitOrder closeOrder = from(openOrder).limitPrice(closePrice).build();
        closeOrder.setNetPrice(closePrice);
        dealer.orderBookSideChanged(new OrderBookSideUpdateEvent(closeSide, emptyList(), asList(closeOrder)));


        BigDecimal diff = ONE.movePointLeft(SCALE);
        if (ASK == side) diff = diff.negate();
        LimitOrder resultingOpenOrder = from(openOrder).limitPrice(openPrice.add(diff)).build();

        // this is highly tuned, mostly because of netPrice inconsistencies
        //verify(orderUpdater).update(argThat(new OrderUpdateEventMatcher(new OrderBinding(openExchange, closeExchange, resultingOpenOrder, asList(Orders.closing(closeOrder))))));


        // step 3: send order book update again, expect no changes
//        reset(orderBookSideMonitor);
        dealer.orderBookSideChanged(new OrderBookSideUpdateEvent(closeSide, emptyList(), asList(closeOrder)));
        verifyZeroInteractions(orderBookSideMonitor);

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