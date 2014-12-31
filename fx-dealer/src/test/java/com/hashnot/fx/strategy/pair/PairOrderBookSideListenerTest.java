package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.IOrderBookSideMonitor;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.framework.OrderBookSideUpdateEvent;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.trade.IOrderTracker;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hashnot.fx.strategy.pair.DealerTest.orderOpenStrategy;
import static com.hashnot.fx.strategy.pair.PairTestUtils.*;
import static com.hashnot.xchange.ext.util.BigDecimals.TWO;
import static com.hashnot.xchange.ext.util.Maps.keys;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;
import static java.math.BigDecimal.ONE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
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
        PollingTradeService tradeService = mock(PollingTradeService.class);

        Exchange openExchange = mock(Exchange.class);
        when(openExchange.getPollingTradeService()).thenReturn(tradeService);

        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange, withSettings().defaultAnswer(RETURNS_DEFAULTS));
        IOrderTracker orderTracker = mock(IOrderTracker.class);
        when(openMonitor.getOrderTracker()).thenReturn(orderTracker);

        Exchange closeExchange = mock(Exchange.class);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange, withSettings().defaultAnswer(RETURNS_DEFAULTS));

        Map<Exchange, IExchangeMonitor> monitors = keys(openExchange, closeExchange).map(openMonitor, closeMonitor);

        BigDecimal openPrice = TWO;
        BigDecimal priceDiff = ONE;
        if (ASK == side)
            priceDiff = priceDiff.negate();
        BigDecimal closePrice = openPrice.add(priceDiff);
        DealerData data = data(openMonitor, openPrice, closeMonitor, closePrice);

        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);

        DealerConfig config = new DealerConfig(side, p);
        Dealer dealer = new Dealer(orderBookSideMonitor, monitors, config, orderOpenStrategy, orderCloseStrategy, data);

        LimitOrder openOrder = new LimitOrder(side, ONE, p, null, null, openPrice);
        MarketSide closeSide = new MarketSide(closeExchange, p, side);

        LimitOrder closeOrder = from(openOrder).limitPrice(closePrice).build();
        List<LimitOrder> closingOrders = asList((closeOrder));
        dealer.orderBookSideChanged(new OrderBookSideUpdateEvent(closeSide, emptyList(), closingOrders));


        //BigDecimal diff = ONE.movePointLeft(SCALE);
        //if (ASK == side) diff = diff.negate();
        //LimitOrder resultingOpenOrder = from(openOrder).limitPrice(openPrice.add(diff)).build();

        // this is highly tuned, mostly because of netPrice inconsistencies
        //verify(orderManager).update(argThat(new OrderUpdateEventMatcher(new OrderBinding(openExchange, closeExchange, resultingOpenOrder, closingOrders))));


        // send order book update again, expect no changes
        dealer.orderBookSideChanged(new OrderBookSideUpdateEvent(closeSide, emptyList(), closingOrders));
        verifyZeroInteractions(orderBookSideMonitor);

    }

}
