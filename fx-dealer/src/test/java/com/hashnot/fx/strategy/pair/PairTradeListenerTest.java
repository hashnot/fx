package com.hashnot.fx.strategy.pair;

import com.google.common.util.concurrent.Futures;
import com.hashnot.fx.framework.IUserTradeMonitor;
import com.hashnot.fx.framework.UserTradeEvent;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class PairTradeListenerTest {

    protected static final CurrencyPair p = CurrencyPair.BTC_EUR;

    @Test
    public void testTrade() throws Exception {
        Exchange openExchange = mock(Exchange.class);

        IExchangeMonitor iem = mock(IExchangeMonitor.class);
        IAsyncTradeService openTradeService = mock(IAsyncTradeService.class);
        when(iem.getTradeService()).thenReturn(openTradeService);
        PairTradeListener orderManager = new PairTradeListener(mock(IUserTradeMonitor.class), new SimpleOrderCloseStrategy(), PairTestUtils.map(openExchange, iem), new DealerData());

        Exchange closeExchange = mock(Exchange.class);
        PollingTradeService closeTradeService = mock(PollingTradeService.class);
        when(closeExchange.getPollingTradeService()).thenReturn(closeTradeService);

        LimitOrder openOrder = new LimitOrder.Builder(ASK, p).limitPrice(ONE).tradableAmount(ONE).build();

        doAnswer(invocation -> {
            ((Consumer<Future<String>>) invocation.getArguments()[1]).accept(Futures.immediateCheckedFuture("ID"));
            return null;
        }).when(openTradeService).placeLimitOrder(eq(openOrder), any(Consumer.class));

        orderManager.update(new OrderBinding(openExchange, closeExchange, openOrder, Arrays.asList(openOrder)));

        verify(openTradeService).placeLimitOrder(eq(openOrder), any(Consumer.class));

        orderManager.trade(new UserTradeEvent(openOrder, new UserTrade(ASK, ONE, p, ONE, null, null, "ID", null, null), null, openExchange));

        LimitOrder closeOrder = LimitOrder.Builder.from(openOrder).orderType(BID).build();
        verify(closeTradeService).placeLimitOrder(closeOrder);
    }
}