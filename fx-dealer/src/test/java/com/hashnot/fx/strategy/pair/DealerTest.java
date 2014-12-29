package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.BestOfferEvent;
import com.hashnot.fx.framework.IOrderBookSideMonitor;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.trade.IOrderTracker;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.Order;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hashnot.fx.strategy.pair.PairTestUtils.*;
import static com.hashnot.xchange.ext.util.BigDecimals.TWO;
import static com.hashnot.xchange.ext.util.BigDecimals._ONE;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static java.math.BigDecimal.ONE;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class DealerTest {


    protected static SimpleOrderOpenStrategy orderOpenStrategy = new SimpleOrderOpenStrategy();

    private Order.OrderType side;

    public DealerTest(Order.OrderType side) {
        this.side = side;
    }


    @Parameterized.Parameters
    public static Iterable<Object[]> getParams() {
        return asList(Order.OrderType.values()).stream().map(t -> new Object[]{t}).collect(Collectors.toList());
    }

    /**
     * register an exchange, it should appear as both open and close
     */
    @Test
    public void testUpdateBestOffer() throws Exception {
        Exchange closeExchange = mock(Exchange.class, "closeExchange");
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange, "closeMonitor");
        Map<Exchange, IExchangeMonitor> monitors = map(closeExchange, closeMonitor);

        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);

        DealerData data = new DealerData();

        Dealer dealer = new Dealer(orderBookSideMonitor, monitors, new DealerConfig(side, p), orderOpenStrategy, orderCloseStrategy, data);

        BigDecimal closePrice = TWO;


        // step 1: register first exchange - close
        MarketSide closeSide = new MarketSide(closeExchange, p, side);
        dealer.updateBestOffers(asList(new BestOfferEvent(closePrice, closeSide)));

        verify(orderTracker, never()).addTradeListener(any());
        verifyZeroInteractions(orderBookSideMonitor);
        assertEquals(new DealerData(closeMonitor, closeMonitor, map(closeExchange, closePrice)), data);
    }

    /**
     * register two exchanges in proper order (close, open) - doesn't re-registers order book listener
     */
    @Test
    public void testUpdateBestOfferBoth() throws Exception {
        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);

        Exchange openExchange = mock(Exchange.class, "openExchange");
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange, "openMonitor");

        Exchange closeExchange = mock(Exchange.class, "closeExchange");
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange, "closeMonitor");
        Map<Exchange, IExchangeMonitor> monitors = map(openExchange, closeExchange, openMonitor, closeMonitor);

        Listener listener = mock(Listener.class, "dealerListener");

        BigDecimal closePrice = TWO;
        DealerData data = data(closeMonitor, closePrice);

        DealerConfig config = new DealerConfig(side, p);
        Dealer dealer = new Dealer(orderBookSideMonitor, monitors, config, orderOpenStrategy, orderCloseStrategy, data);
        dealer.setListener(listener);


        // step 2: register second exchange - open
        MarketSide openSide = new MarketSide(openExchange, p, side);

        BigDecimal openPrice = closePrice.add(side == ASK ? ONE : _ONE);
        dealer.updateBestOffers(asList(new BestOfferEvent(openPrice, openSide)));

        verify(orderTracker, never()).addTradeListener(any());

        verify(orderBookSideMonitor).addOrderBookSideListener(eq(listener), eq(new MarketSide(closeExchange, p, side)));
        verifyNoMoreInteractions(orderBookSideMonitor);
        assertEquals(new DealerData(openMonitor, closeMonitor, map(openExchange, closeExchange, openPrice, closePrice)), data);
    }

    @Test
    public void testUpdateBestOfferBothReverse() throws Exception {
        Exchange openExchange = mock(Exchange.class, "openExchange");
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange, "openMonitor");

        Exchange closeExchange = mock(Exchange.class, "closeExchange");
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange, "closeMonitor");
        Map<Exchange, IExchangeMonitor> monitors = map(openExchange, closeExchange, openMonitor, closeMonitor);

        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);

        Listener listener = mock(Listener.class, "dealerListener");

        // step 1: register first exchange - open
        BigDecimal closePrice = TWO;
        BigDecimal openPrice = closePrice.add(side == ASK ? ONE : _ONE);
        DealerData data = data(openMonitor, openPrice);
        DealerConfig config = new DealerConfig(side, p);
        Dealer dealer = new Dealer(orderBookSideMonitor, monitors, config, orderOpenStrategy, orderCloseStrategy, data);
        dealer.setListener(listener);


        // step 2: register first exchange - close
        MarketSide closeSide = new MarketSide(closeExchange, p, side);
        dealer.updateBestOffers(asList(new BestOfferEvent(closePrice, closeSide)));

        verify(orderTracker, never()).addTradeListener(any());
        verify(orderBookSideMonitor).addOrderBookSideListener(eq(listener), eq(closeSide));
        assertEquals(new DealerData(openMonitor, closeMonitor, map(openExchange, closeExchange, openPrice, closePrice)), data);
    }

    protected DealerData data(IExchangeMonitor exchange, BigDecimal price) {
        return new DealerData(exchange, exchange, map(exchange.getExchange(), price));
    }

}