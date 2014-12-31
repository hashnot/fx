package com.hashnot.fx.strategy.pair;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.hashnot.fx.framework.IOrderBookSideMonitor;
import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.trade.IOrderTracker;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.Future;
import java.util.function.Consumer;

import static com.hashnot.xchange.ext.util.Maps.keys;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@Ignore("orderManager was internalized")
public class PairTradeListenerTest {

    protected static final CurrencyPair p = CurrencyPair.BTC_EUR;

    @Test
    public void testTrade() throws Exception {
        Exchange openExchange = mock(Exchange.class);

        IExchangeMonitor iem = mock(IExchangeMonitor.class);
        IAsyncExchange ox = mock(IAsyncExchange.class);
        IAsyncTradeService openTradeService = mock(IAsyncTradeService.class);
        when(iem.getAsyncExchange()).thenReturn(ox);
        when(ox.getTradeService()).thenReturn(openTradeService);
        when(iem.getOrderTracker()).thenReturn(mock(IOrderTracker.class));

        IExchangeMonitor closeMonitor = mock(IExchangeMonitor.class);
        Exchange closeExchange = mock(Exchange.class);
        IAsyncTradeService closeTradeService = mock(IAsyncTradeService.class);
        IAsyncExchange cx = mock(IAsyncExchange.class);
        when(closeMonitor.getAsyncExchange()).thenReturn(cx);
        when(cx.getTradeService()).thenReturn(closeTradeService);

        Dealer orderManager = new Dealer(mock(IOrderBookSideMonitor.class), keys(openExchange, closeExchange).map(iem, closeMonitor), new DealerConfig(null, null), new SimpleOrderOpenStrategy(), new SimpleOrderCloseStrategy(), new DealerData());

        LimitOrder openOrder = new LimitOrder.Builder(ASK, p).limitPrice(ONE).tradableAmount(ONE).build();

        doAnswer(invocation -> {
            CheckedFuture<String, Exception> result = Futures.immediateCheckedFuture("ID");

            @SuppressWarnings("unchecked")
            Consumer<Future<String>> futureConsumer = (Consumer) invocation.getArguments()[1];

            futureConsumer.accept(result);
            return result;
        }).when(openTradeService).placeLimitOrder(eq(openOrder), any(Consumer.class));

/*
        orderManager.update(new OrderBinding(openExchange, closeExchange, openOrder, Arrays.asList(openOrder)));

        verify(openTradeService).placeLimitOrder(eq(openOrder), anyConsumer);
*/

        orderManager.trades(asList(new UserTradeEvent(openOrder, new UserTrade(ASK, ONE, p, ONE, null, null, "ID", null, null), null, openExchange)));

        LimitOrder closeOrder = LimitOrder.Builder.from(openOrder).orderType(BID).build();
        verify(closeTradeService).placeLimitOrder(eq(closeOrder), any(Consumer.class));
    }
}