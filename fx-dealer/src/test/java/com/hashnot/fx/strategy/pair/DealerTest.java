package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.BestOfferEvent;
import com.hashnot.fx.framework.IOrderBookSideMonitor;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.trade.IOrderTracker;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hashnot.fx.strategy.pair.PairTestUtils.*;
import static com.hashnot.xchange.ext.util.BigDecimals.TWO;
import static com.hashnot.xchange.ext.util.BigDecimals._ONE;
import static com.hashnot.xchange.ext.util.Maps.keys;
import static com.hashnot.xchange.ext.util.Maps.map;
import static com.hashnot.xchange.ext.util.Orders.*;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(Parameterized.class)
public class DealerTest {
    final static private Logger log = LoggerFactory.getLogger(DealerTest.class);


    public static final String X_OPEN = "openExchange";
    public static final String X_CLOSE = "closeExchange";
    public static final String M_OPEN = "openMonitor";
    public static final String M_CLOSE = "closeMonitor";
    protected static SimpleOrderOpenStrategy orderOpenStrategy = new SimpleOrderOpenStrategy();

    private Order.OrderType side;

    public DealerTest(Order.OrderType side) {
        this.side = side;
    }

    static {
        Mockito.withSettings().invocationListeners(e -> log.debug("{}", e));
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
        Exchange closeExchange = mock(Exchange.class, X_CLOSE);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange, M_CLOSE);
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

        Exchange openExchange = mock(Exchange.class, X_OPEN);
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange, M_OPEN);

        Exchange closeExchange = mock(Exchange.class, X_CLOSE);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange, M_CLOSE);
        Map<Exchange, IExchangeMonitor> monitors = keys(openExchange, closeExchange).map(openMonitor, closeMonitor);

        Listener listener = mock(Listener.class, "dealerListener");

        BigDecimal closePrice = TWO;
        BigDecimal openPrice = closePrice.add(side == ASK ? ONE : _ONE);
        DealerData data = data(openMonitor, openPrice);

        DealerConfig config = new DealerConfig(side, p);
        Dealer dealer = new Dealer(orderBookSideMonitor, monitors, config, orderOpenStrategy, orderCloseStrategy, data);
        dealer.setListener(listener);


        dealer.updateBestOffer(closeExchange, closePrice);

        verify(orderTracker, never()).addTradeListener(any());

        //verify(orderBookSideMonitor).removeOrderBookSideListener(eq(listener), eq(new MarketSide(openExchange, p, side)));
        verify(orderBookSideMonitor).addOrderBookSideListener(eq(listener), eq(new MarketSide(closeExchange, p, side)));
        assertEquals(new DealerData(openMonitor, closeMonitor, keys(openExchange, closeExchange).map(openPrice, closePrice)), data);
    }

    @Test
    public void testUpdateBestOfferBothReverse() throws Exception {
        Exchange openExchange = mock(Exchange.class, X_OPEN);
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange, M_OPEN);

        Exchange closeExchange = mock(Exchange.class, X_CLOSE);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange, M_CLOSE);
        Map<Exchange, IExchangeMonitor> monitors = keys(openExchange, closeExchange).map(openMonitor, closeMonitor);

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
        dealer.updateBestOffer(closeExchange, closePrice);

        verify(orderTracker, never()).addTradeListener(any());
        verify(orderBookSideMonitor).addOrderBookSideListener(eq(listener), eq(closeSide));
        assertEquals(new DealerData(openMonitor, closeMonitor, keys(openExchange, closeExchange).map(openPrice, closePrice)), data);
    }

    BigDecimal FEE = ONE.movePointLeft(3);

    @Test
    public void testProfitableOpenPrice() throws Exception {
        BigDecimal closePrice = TWO;
        BigDecimal openPrice = closePrice.add(side == ASK ? ONE : _ONE);
        Exchange openExchange = mock(Exchange.class, X_OPEN);
        Exchange closeExchange = mock(Exchange.class, X_CLOSE);
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange, M_OPEN, FEE);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange, M_CLOSE, FEE);

        Map<Exchange, BigDecimal> bestOffers = keys(openExchange, closeExchange).map(openPrice, closePrice);

        DealerData data = new DealerData(openMonitor, closeMonitor, bestOffers);
        Dealer dealer = createDealer(data, keys(openExchange, closeExchange).map(openMonitor, closeMonitor));

        BigDecimal expected = openPrice.add(bigFactor(revert(side)).multiply(openMonitor.getMarketMetadata(p).getPriceStep()));

        BigDecimal price = dealer.profitableOpenPrice();
        log.info("{}", price);
        assertEquals(expected, price);

        Map<String, BigDecimal> open = keys(p.baseSymbol, p.counterSymbol).map(TEN, TEN);
        //Map<String, BigDecimal> close = new HashMap<>(open);
        Map<String, BigDecimal> wallet = simulateTrade(new LimitOrder(side, ONE, p, null, null, price), open, openMonitor.getMarketMetadata(p));
        log.debug("{}", wallet);
    }

    @Test
    public void testUpdateOpenExchange() throws Exception {
        BigDecimal closePrice = TEN;
        BigDecimal factor = side == ASK ? ONE : _ONE;
        BigDecimal openPrice = closePrice.add(factor);
        Exchange openExchange = mock(Exchange.class, X_OPEN);
        Exchange closeExchange = mock(Exchange.class, X_CLOSE);
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange, M_OPEN);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange, M_CLOSE);

        Map<Exchange, BigDecimal> bestOffers = keys(openExchange, closeExchange).map(openPrice, closePrice);

        DealerData data = new DealerData(openMonitor, closeMonitor, bestOffers);
        //log.info("{}", data);
        Dealer dealer = createDealer(data, keys(openExchange, closeExchange).map(openMonitor, closeMonitor));

        dealer.updateBestOffer(openExchange, closePrice.subtract(factor));
        //log.info("{}", data);

        assertEquals(data.getOpenExchange(), closeMonitor);
        assertEquals(data.getCloseExchange(), openMonitor);
    }

    @Test
    public void testUpdateCloseExchange() throws Exception {
        BigDecimal closePrice = TEN;
        BigDecimal factor = side == ASK ? ONE : _ONE;
        BigDecimal openPrice = closePrice.add(factor);
        Exchange openExchange = mock(Exchange.class, X_OPEN);
        Exchange closeExchange = mock(Exchange.class, X_CLOSE);
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange, M_OPEN);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange, M_CLOSE);

        Map<Exchange, BigDecimal> bestOffers = keys(openExchange, closeExchange).map(openPrice, closePrice);

        DealerData data = new DealerData(openMonitor, closeMonitor, bestOffers);
        //log.info("{}", data);
        Dealer dealer = createDealer(data, keys(openExchange, closeExchange).map(openMonitor, closeMonitor));

        dealer.updateBestOffer(closeExchange, closePrice.add(factor.multiply(TWO)));
        //log.info("{}", data);

        assertEquals(data.getOpenExchange(), closeMonitor);
        assertEquals(data.getCloseExchange(), openMonitor);
    }

    protected DealerData data(IExchangeMonitor exchange, BigDecimal price) {
        return new DealerData(exchange, exchange, map(exchange.getExchange(), price));
    }

    private Dealer createDealer(DealerData data, Map<Exchange, IExchangeMonitor> monitors) {
        IOrderBookSideMonitor orderBookSideMonitor = mock(IOrderBookSideMonitor.class);

        Listener listener = mock(Listener.class, "dealerListener");

        // step 1: register first exchange - open
        DealerConfig config = new DealerConfig(side, p);
        Dealer dealer = new Dealer(orderBookSideMonitor, monitors, config, orderOpenStrategy, orderCloseStrategy, data);
        dealer.setListener(listener);
        return dealer;
    }
}