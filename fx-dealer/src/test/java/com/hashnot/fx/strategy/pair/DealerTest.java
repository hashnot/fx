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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hashnot.fx.strategy.pair.PairTestUtils.*;
import static com.hashnot.xchange.ext.util.Numbers.BigDecimal._ONE;
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
        Exchange closeExchange = mock(Exchange.class);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange);
        Map<Exchange, IExchangeMonitor> monitors = map(closeExchange, closeMonitor);

        IOrderTracker orderTracker = mock(IOrderTracker.class);
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);

        DealerData data = new DealerData();

        Dealer dealer = new Dealer(orderBookSideMonitor, new PairTradeListener(orderTracker, orderCloseStrategy, monitors, data), monitors, new DealerConfig(side, p), orderOpenStrategy, data);

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

        PairTradeListener orderManager = new PairTradeListener(orderTracker, orderCloseStrategy, monitors, data);
        DealerConfig config = new DealerConfig(side, p);
        PairOrderBookSideListener orderBookSideListener = new PairOrderBookSideListener(config, data, orderManager, orderOpenStrategy, monitors);
        Dealer dealer = new Dealer(orderBookSideMonitor, orderManager, monitors, config, orderBookSideListener, data);


        // step 2: register second exchange - open
        MarketSide openSide = new MarketSide(openExchange, p, side);

        BigDecimal openPrice = closePrice.add(side == ASK ? ONE : _ONE);
        dealer.updateBestOffer(new BestOfferEvent(openPrice, openSide));

        verify(orderTracker, never()).addTradeListener(any(), any());

        verify(orderBookSideMonitor).addOrderBookSideListener(eq(orderBookSideListener), eq(new MarketSide(closeExchange, p, side)));
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
        PairTradeListener orderManager = new PairTradeListener(orderTracker, orderCloseStrategy, monitors, data);
        DealerConfig config = new DealerConfig(side, p);
        PairOrderBookSideListener orderBookSideListener = new PairOrderBookSideListener(config, data, orderManager, orderOpenStrategy, monitors);
        Dealer dealer = new Dealer(orderBookSideMonitor, orderManager, monitors, config, orderBookSideListener, data);


        // step 2: register first exchange - close
        MarketSide closeSide = new MarketSide(closeExchange, p, side);
        dealer.updateBestOffer(new BestOfferEvent(closePrice, closeSide));

        verify(orderTracker, never()).addTradeListener(any(), any());
        verify(orderBookSideMonitor).addOrderBookSideListener(eq(orderBookSideListener), eq(closeSide));
        assertEquals(new DealerData(openExchange, closeExchange, map(openExchange, openPrice, closeExchange, closePrice)), data);
    }

    protected DealerData data(Exchange exchange, BigDecimal price) {
        return new DealerData(exchange, exchange, map(exchange, price));
    }

}