package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.BestOfferEvent;
import com.hashnot.fx.framework.IOrderBookSideMonitor;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.uttl.BigDecimalMatcher;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.account.IWalletMonitor;
import com.hashnot.xchange.event.trade.IOrderTracker;
import com.hashnot.xchange.ext.util.Maps;
import com.hashnot.xchange.ext.util.Prices;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.MarketMetaData;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hashnot.fx.strategy.pair.PairTestUtils.*;
import static com.hashnot.xchange.ext.util.BigDecimals.TWO;
import static com.hashnot.xchange.ext.util.BigDecimals._ONE;
import static com.hashnot.xchange.ext.util.Maps.keys;
import static com.hashnot.xchange.ext.util.Maps.map;
import static com.hashnot.xchange.ext.util.Orders.*;
import static com.xeiam.xchange.currency.Currencies.BTC;
import static com.xeiam.xchange.currency.Currencies.EUR;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.junit.Assert.*;
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

    private DealerConfig config;
    static CurrencyPair P = CurrencyPair.BTC_EUR;

    public DealerTest(Order.OrderType side) {
        this.side = side;
        config = new DealerConfig(side, P);
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
//        verifyZeroInteractions(orderBookSideMonitor);
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


        dealer.updateBestOffers(Arrays.asList(new BestOfferEvent(closePrice, new MarketSide(closeExchange, P, side))));

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
        dealer.updateBestOffers(Arrays.asList(new BestOfferEvent(closePrice, closeSide)));

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
        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange, M_OPEN);
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange, M_CLOSE);

        Map<Exchange, BigDecimal> bestOffers = keys(openExchange, closeExchange).map(openPrice, closePrice);

        DealerData data = new DealerData(openMonitor, closeMonitor, bestOffers);
        Dealer dealer = createDealer(data, keys(openExchange, closeExchange).map(openMonitor, closeMonitor));

        BigDecimal expected = openPrice.add(bigFactor(revert(side)).multiply(Prices.getStep(openMonitor.getMarketMetadata(p).getPriceScale())));

        BigDecimal price = dealer.profitableOpenPrice();
        log.info("{}", price);
        assertEquals(expected, price);

        Map<String, BigDecimal> open = keys(p.baseSymbol, p.counterSymbol).map(TEN, TEN);
        //Map<String, BigDecimal> close = new HashMap<>(open);
        Map<String, BigDecimal> wallet = simulateTrade(new LimitOrder(side, ONE, p, null, null, price), open, openMonitor.getAccountInfo());
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

        dealer.updateBestOffers(Arrays.asList(new BestOfferEvent(closePrice.subtract(factor), new MarketSide(openExchange, P, side))));

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

        dealer.updateBestOffers(Arrays.asList(new BestOfferEvent(closePrice.add(factor.multiply(TWO)), new MarketSide(closeExchange, P, side))));

        assertEquals(data.getOpenExchange(), closeMonitor);
        assertEquals(data.getCloseExchange(), openMonitor);
    }

    @Test
    public void testHasMinimumMoney() {
        BigDecimal closePrice = TEN;
        BigDecimal factor = side == ASK ? ONE : _ONE;
        BigDecimal openPrice = closePrice.add(factor);
        Exchange openExchange = mock(Exchange.class, X_OPEN);
        Exchange closeExchange = mock(Exchange.class, X_CLOSE);

        String inCur, outCur;
        if (side == BID) {
            inCur = BTC;
            outCur = EUR;
        } else {
            inCur = EUR;
            outCur = BTC;
        }

        IExchangeMonitor openMonitor = getExchangeMonitor(openExchange, M_OPEN, Maps.keys(inCur, outCur).map(ONE.movePointLeft(8), ONE));
        IExchangeMonitor closeMonitor = getExchangeMonitor(closeExchange, M_CLOSE, Maps.keys(inCur, outCur).map(TEN,ONE.movePointLeft(8)));

        Map<Exchange, BigDecimal> bestOffers = keys(openExchange, closeExchange).map(openPrice, closePrice);

        DealerData data = new DealerData(openMonitor, closeMonitor, bestOffers);
        Dealer dealer = createDealer(data, keys(openExchange, closeExchange).map(openMonitor, closeMonitor));

        assertTrue(dealer.hasMinimumMoney(openExchange, false));
        assertTrue(dealer.hasMinimumMoney(closeExchange, true));
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

    /**
     * Standard deal: deal amount: as much as can close, wallet at least 2x
     */
    @Test
    public void testDeal() throws Exception {
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(TWO, v("-.2"))),
                order(side, ONE, P, p(TWO, v("-.1")))
        );
        IExchangeMonitor openMonitor = getExchangeMonitor(mock(Exchange.class, X_OPEN));
        IExchangeMonitor closeMonitor = getExchangeMonitor(mock(Exchange.class, X_CLOSE));
        DealerData data = new DealerData(openMonitor, closeMonitor, emptyMap());
        data.closingOrders = closeOrders;
        Dealer dealer = new Dealer(mock(IOrderBookSideMonitor.class), emptyMap(), config, orderOpenStrategy, mock(SimpleOrderCloseStrategy.class), data);

        LimitOrder deal = dealer.deal(TWO);

        assertNotNull(deal);
        log.info("deal={}", deal);
        BigDecimal amount = deal.getTradableAmount();
        assertThat(amount, new BigDecimalMatcher(ONE));
    }

    /**
     * Deal with amount of half the wallet @open exchange
     */
    @Test
    public void testDealHalfOpenWallet() throws Exception {
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, TEN, P, p(ONE, v("-.1")))
        );


        DealerData data = new DealerData(m("myOpenExchange", v(2)), m2, new HashMap<>());
        data.closingOrders = closeOrders;
        Dealer dealer = new Dealer(mock(IOrderBookSideMonitor.class), emptyMap(), config, orderOpenStrategy, mock(SimpleOrderCloseStrategy.class), data);

        LimitOrder deal = dealer.deal(ONE);

        assertNotNull(deal);
        assertThat("tradableAmount", deal.getTradableAmount(), new BigDecimalMatcher(ONE));
    }

    @Test
    public void testDealHalfCloseWallet() throws Exception {
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, ONE, P, p(ONE, v("-.1")))
        );


        DealerData data = new DealerData(m1, m("myCloseExchange", v(2)), new HashMap<>());
        data.closingOrders = closeOrders;
        Dealer dealer = new Dealer(mock(IOrderBookSideMonitor.class), emptyMap(), config, orderOpenStrategy, mock(SimpleOrderCloseStrategy.class), data);

        LimitOrder deal = dealer.deal(ONE);

        assertNotNull(deal);
        assertThat("tradableAmount", deal.getTradableAmount(), new BigDecimalMatcher(ONE));
    }


    @Test
    public void testDealLowOpenWallet() throws Exception {
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, ONE, P, p(ONE, v("-.1")))
        );

        BigDecimal small = ONE.movePointLeft(2);
        DealerData data = new DealerData(m("myOpenExchange", small), m2, emptyMap());
        data.closingOrders = closeOrders;
        Dealer dealer = new Dealer(mock(IOrderBookSideMonitor.class), emptyMap(), config, orderOpenStrategy, mock(SimpleOrderCloseStrategy.class), data);

        LimitOrder deal = dealer.deal(ONE);

        if (side == dealer.skipSide) {
            // skip BID
            assertNull(deal);
        } else {
            assertNotNull(deal);
            assertThat("tradableAmount", deal.getTradableAmount(), new BigDecimalMatcher(small));
        }
    }

    @Test
    public void testDealLowCloseWallet() throws Exception {
        List<LimitOrder> closeOrders = asList(
                order(side, v(".5"), P, p(ONE, v("-.2"))),
                order(side, ONE, P, p(ONE, v("-.1")))
        );

        BigDecimal small = v(".01");
        DealerData data = new DealerData(m1, m("myCloseExchange", small), emptyMap());
        data.closingOrders = closeOrders;
        Dealer dealer = new Dealer(mock(IOrderBookSideMonitor.class), emptyMap(), config, orderOpenStrategy, mock(SimpleOrderCloseStrategy.class), data);

        LimitOrder deal = dealer.deal(ONE);

        if (side == dealer.skipSide) {
            // skip BID
            assertNull(deal);
        } else {
            assertNotNull(deal);
            assertThat("tradableAmount", deal.getTradableAmount(), new BigDecimalMatcher(small));
        }
    }

    protected static LimitOrder order(Order.OrderType type, BigDecimal tradableAmount, CurrencyPair pair, BigDecimal limitPrice) {
        return new LimitOrder(type, tradableAmount, pair, null, null, limitPrice);
    }

    protected BigDecimal p(BigDecimal base, BigDecimal delta) {
        BigDecimal myDelta = side == Order.OrderType.ASK ? delta : delta.negate();
        return base.add(myDelta);
    }

    IExchangeMonitor m1 = m("exchange1");
    IExchangeMonitor m2 = m("exchange2");

    private IExchangeMonitor m(String name) {
        return m(name, TEN);
    }

    private IExchangeMonitor m(String name, BigDecimal walletAmount) {
        IWalletMonitor walletMon = mock(IWalletMonitor.class, "Monitor:" + name);
        when(walletMon.getWallet(any())).thenReturn(walletAmount);
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> wallet = mock(Map.class);
        when(wallet.get(any(String.class))).thenReturn(walletAmount);
        when(walletMon.getWallet()).thenReturn(wallet);
        return m(name, walletMon);
    }

    private IExchangeMonitor m(String name, IWalletMonitor walletMon) {
        IExchangeMonitor m = mock(IExchangeMonitor.class, name);
        when(m.getWalletMonitor()).thenReturn(walletMon);
        when(m.getMarketMetadata(any())).thenReturn(new MarketMetaData(ONE.movePointLeft(2), ONE.movePointLeft(2), 2));
        when(m.getAccountInfo()).thenReturn(new AccountInfo(null, FEE, null));
        return m;
    }
}
