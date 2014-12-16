package com.hashnot.xchange.event.account;

import com.hashnot.xchange.event.trade.IOrderTracker;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.hashnot.xchange.ext.trade.OrderPlacementEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;
import org.junit.Test;

import static com.hashnot.xchange.ext.util.BigDecimals.TWO;
import static java.math.BigDecimal.ONE;
import static org.mockito.Mockito.*;

public class WalletTrackerTest {

    private static final String orderId = "id";

    @Test
    public void testWalletUpdate() throws Exception {
        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IWalletMonitor walletMonitor = mock(IWalletMonitor.class);
        Exchange exchange = mock(Exchange.class);
        LimitOrder monitored = new LimitOrder.Builder(Order.OrderType.ASK, CurrencyPair.BTC_EUR).id(orderId).tradableAmount(TWO).build();

        WalletTracker walletTracker = new WalletTracker(orderTracker, walletMonitor);

        walletTracker.limitOrderPlaced(new OrderPlacementEvent<>(orderId, monitored, exchange));
        verify(orderTracker).addTradeListener(walletTracker);


        UserTrade trade = new UserTrade(Order.OrderType.ASK, ONE, CurrencyPair.BTC_EUR, ONE, null, null, orderId, null, null);
        LimitOrder current = LimitOrder.Builder.from(monitored).tradableAmount(ONE).build();
        walletTracker.trade(new UserTradeEvent(monitored, trade, current, exchange));
        verify(walletMonitor).update();
        reset(walletMonitor);
        verifyZeroInteractions(orderTracker);


        walletTracker.trade(new UserTradeEvent(monitored, trade, null, exchange));
        verify(walletMonitor).update();
        verify(orderTracker).removeTradeListener(walletTracker);
        verifyNoMoreInteractions(walletMonitor);
    }
}