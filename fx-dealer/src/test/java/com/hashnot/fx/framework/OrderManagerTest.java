package com.hashnot.fx.framework;

import com.hashnot.fx.dealer.OrderBinding;
import com.hashnot.fx.dealer.OrderManager;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.junit.Test;

import java.util.Arrays;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OrderManagerTest {

    protected static final CurrencyPair p = CurrencyPair.BTC_EUR;

    @Test
    public void testTrade() throws Exception {
        OrderManager orderManager = new OrderManager(mock(IUserTradeMonitor.class));
        Exchange openExchange = mock(Exchange.class);
        PollingTradeService openTradeService = mock(PollingTradeService.class);
        when(openExchange.getPollingTradeService()).thenReturn(openTradeService);

        Exchange closeExchange = mock(Exchange.class);
        PollingTradeService closeTradeService = mock(PollingTradeService.class);
        when(closeExchange.getPollingTradeService()).thenReturn(closeTradeService);

        LimitOrder openOrder = new LimitOrder.Builder(ASK, p).limitPrice(ONE).tradableAmount(ONE).build();
        LimitOrder closeOrder = LimitOrder.Builder.from(openOrder).orderType(BID).build();

        when(openTradeService.placeLimitOrder(eq(openOrder))).thenReturn("ID");

        orderManager.update(new OrderBinding(openExchange, closeExchange, openOrder, Arrays.asList(closeOrder)));

        verify(openTradeService).placeLimitOrder(eq(openOrder));

        orderManager.trade(new UserTradeEvent(openOrder, new UserTrade(ASK, ONE, p, ONE, null, null, "ID", null, null), null, openExchange));

        verify(closeTradeService).placeLimitOrder(closeOrder);
    }
}