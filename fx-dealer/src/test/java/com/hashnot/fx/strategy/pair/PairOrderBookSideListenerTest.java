package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.*;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hashnot.fx.strategy.pair.DealerTest.orderOpenStrategy;
import static com.hashnot.fx.strategy.pair.PairTestUtils.*;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.ONE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class PairOrderBookSideListenerTest {
    private Order.OrderType side;

    public PairOrderBookSideListenerTest(Order.OrderType side) {
        this.side = side;
    }

    @Parameterized.Parameters
    public static Iterable<Object[]> getParams() {
        return asList(Order.OrderType.values()).stream().map(t -> new Object[]{t}).collect(Collectors.toList());
    }

    @Test
    public void testOrderBookSideChanged() throws Exception {
        ITradeService tradeService = mock(ITradeService.class);

        Exchange openExchange = mock(Exchange.class);
        when(openExchange.getPollingTradeService()).thenReturn(tradeService);

        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange);
        Exchange closeExchange = mock(Exchange.class);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange);
        Map<Exchange, IExchangeMonitor> monitors = map(openExchange, openMonitor, closeExchange, closeMonitor);

        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);
        OrderManager orderManager = spy(new OrderManager(orderTracker, orderCloseStrategy));

        BigDecimal openPrice = new BigDecimal(2);
        BigDecimal priceDiff = ONE;
        if (ASK == side)
            priceDiff = priceDiff.negate();
        BigDecimal closePrice = openPrice.add(priceDiff);


        DealerData data = data(openExchange, openPrice, closeExchange, closePrice);
        DealerConfig config = new DealerConfig(side, p);
        PairOrderBookSideListener orderBookSideListener = new PairOrderBookSideListener(config, data, orderManager, orderOpenStrategy, monitors);


        LimitOrder openOrder = new LimitOrder(side, ONE, p, null, null, openPrice);
        openOrder.setNetPrice(openPrice);
        MarketSide closeSide = new MarketSide(closeExchange, p, side);

        LimitOrder closeOrder = from(openOrder).limitPrice(closePrice).build();
        closeOrder.setNetPrice(closePrice);
        List<LimitOrder> closingOrders = asList((closeOrder));
        orderBookSideListener.orderBookSideChanged(new OrderBookSideUpdateEvent(closeSide, emptyList(), closingOrders));


        BigDecimal diff = ONE.movePointLeft(SCALE);
        if (ASK == side) diff = diff.negate();
        LimitOrder resultingOpenOrder = from(openOrder).limitPrice(openPrice.add(diff)).build();

        // this is highly tuned, mostly because of netPrice inconsistencies
        verify(orderManager).update(argThat(new OrderUpdateEventMatcher(new OrderBinding(openExchange, closeExchange, resultingOpenOrder, closingOrders))));


        // send order book update again, expect no changes
        orderBookSideListener.orderBookSideChanged(new OrderBookSideUpdateEvent(closeSide, emptyList(), closingOrders));
        verifyZeroInteractions(orderBookSideMonitor);

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
