package com.hashnot.xchange.ext.util;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.BaseMarketMetadata;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Map;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class OrdersTest {

    public static final CurrencyPair P = CurrencyPair.BTC_EUR;

    /**
     * ASK sells (decreases the wallet of) base currency while buys (increases the wallet of) counter currency, and subtracts the fee
     */
    @Test
    public void testSimulateTradeAsk() throws Exception {
        LimitOrder order = new LimitOrder.Builder(ASK, P).limitPrice(ONE).tradableAmount(ONE).build();
        BigDecimal percent = BigDecimal.ONE.movePointLeft(2);
        MarketMetadata meta = new BaseMarketMetadata(percent, 2, percent);
        Map<String, BigDecimal> walletIn = Maps.keys(P.baseSymbol, P.counterSymbol).map(TEN, TEN);

        Map<String, BigDecimal> walletOut = Maps.map(asList(P.baseSymbol, P.counterSymbol), asList(valueOf(9), valueOf("10.99")));

        Map<String, BigDecimal> result = Orders.simulateTrade(order, walletIn, meta);
        assertEquals(result, walletOut);
    }

    /**
     * BID sells (decreases the wallet of) counter currency while buys (increases the wallet of) base currency, and subtracts the fee from counter wallet
     */
    @Test
    public void testSimulateTradeBid() throws Exception {
        LimitOrder order = new LimitOrder.Builder(BID, P).limitPrice(ONE).tradableAmount(ONE).build();
        BigDecimal percent = BigDecimal.ONE.movePointLeft(2);
        MarketMetadata meta = new BaseMarketMetadata(percent, 2, percent);
        Map<String, BigDecimal> walletIn = Maps.keys(P.baseSymbol, P.counterSymbol).map(TEN, TEN);

        Map<String, BigDecimal> walletOut = Maps.map(asList(P.baseSymbol, P.counterSymbol), asList(valueOf(11), valueOf("8.99")));

        Map<String, BigDecimal> result = Orders.simulateTrade(order, walletIn, meta);
        assertEquals(result, walletOut);
    }

    private BigDecimal valueOf(String s) {
        return new BigDecimal(s);
    }

    private BigDecimal valueOf(long s) {
        return BigDecimal.valueOf(s);
    }
}