package com.hashnot.fx.dealer;

import com.hashnot.fx.framework.*;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.BaseMarketMetadata;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.*;
import static java.util.Collections.emptyList;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;

public class DealerTest {

    protected static final CurrencyPair p = CurrencyPair.BTC_EUR;
    protected Order.OrderType side = Order.OrderType.ASK;

    @Test
    public void testUpdateBestOffer() throws Exception {
        Map<Exchange, IExchangeMonitor> monitors = new HashMap<>();
        Exchange openExchange = mock(Exchange.class);
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange);
        monitors.put(openExchange, openMonitor);

        Exchange closeExchange = mock(Exchange.class);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange);
        monitors.put(closeExchange, closeMonitor);

        IOrderUpdater orderUpdater = mock(IOrderUpdater.class);
        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);

        Dealer dealer = new Dealer(new Simulation(monitors), orderUpdater, orderBookSideMonitor, orderTracker, monitors, side, p);


        // step 1: register first exchange - close
        MarketSide closeSide = new MarketSide(closeExchange, p, side);
        dealer.updateBestOffer(new BestOfferEvent(ONE, closeSide));

        // TODO verify(orderUpdater, calls(0)).update(any());
        verify(orderTracker, never()).addTradeListener(any(), any());
        // TODO verifyZeroInteractions(orderBookSideMonitor);


        // step 2: register second exchange - open
        MarketSide openSide = new MarketSide(openExchange, p, side);
        dealer.updateBestOffer(new BestOfferEvent(new BigDecimal(2), openSide));

        // TODO verify(orderUpdater, calls(0)).update(any());
        reset(orderUpdater);
        verify(orderTracker, never()).addTradeListener(any(), any());
        verify(orderBookSideMonitor).addOrderBookSideListener(eq(dealer), eq(closeSide));
    }

    @Test
    public void testOrderBookSideChanged() throws Exception {
        Map<Exchange, IExchangeMonitor> monitors = new HashMap<>();
        Exchange openExchange = mock(Exchange.class);
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange);
        monitors.put(openExchange, openMonitor);

        Exchange closeExchange = mock(Exchange.class);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange);
        monitors.put(closeExchange, closeMonitor);

        IOrderUpdater orderUpdater = mock(IOrderUpdater.class);
        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);

        Dealer dealer = new Dealer(new Simulation(monitors), orderUpdater, orderBookSideMonitor, orderTracker, monitors, side, p);

        MarketSide closeSide = new MarketSide(closeExchange, p, side);
        dealer.updateBestOffer(new BestOfferEvent(ONE, closeSide));


        // step 2: register second exchange - open
        MarketSide openSide = new MarketSide(openExchange, p, side);
        BigDecimal bestPrice = new BigDecimal(2);
        dealer.updateBestOffer(new BestOfferEvent(bestPrice, openSide));

        reset(orderUpdater);

        // step 3: send order book
        LimitOrder order = new LimitOrder(side, ONE, p, null, null, ONE);
        List<LimitOrder> orders = Arrays.asList(order);
        dealer.orderBookSideChanged(new OrderBookSideUpdateEvent(closeSide, emptyList(), orders));


        // this is highly tuned, mostly because of netPrice inconsistencies
        verify(orderUpdater).update(argThat(new OrderUpdateEventMatcher(new OrderUpdateEvent(openExchange, closeExchange, from(order).limitPrice(bestPrice).build(), Arrays.asList(Orders.closing(order))))));


    }

    private static IExchangeMonitor getExchangeMonitor(Exchange x) {
        IExchangeMonitor monitor = mock(IExchangeMonitor.class);
        when(monitor.getMarketMetadata(p)).thenReturn(new BaseMarketMetadata(ONE.movePointLeft(5), 5, ZERO));
        when(monitor.getWallet(any())).thenReturn(TEN);
        when(monitor.getLimit(any())).thenReturn(new BigDecimal(2));
        when(monitor.getExchange()).thenReturn(x);
        return monitor;
    }

    private static class OrderUpdateEventMatcher extends TypeSafeMatcher<OrderUpdateEvent> {
        private OrderUpdateEvent a;

        public OrderUpdateEventMatcher(OrderUpdateEvent evt) {
            a = evt;
        }

        @Override
        protected boolean matchesSafely(OrderUpdateEvent b) {
            return a.clear == b.clear
                    && a.closeExchange == b.closeExchange
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