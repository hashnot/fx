package com.hashnot.fx.strategy.pair;

import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.IWalletMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.BaseMarketMetadata;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.ZERO;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Rafał Krupiński
 */
public class PairTestUtils {
    protected static final CurrencyPair p = CurrencyPair.BTC_EUR;
    protected static final int SCALE = 5;
    protected static SimpleOrderCloseStrategy orderCloseStrategy = new SimpleOrderCloseStrategy();

    protected static IExchangeMonitor getExchangeMonitor(Exchange x) {
        IExchangeMonitor monitor = mock(IExchangeMonitor.class);

        IWalletMonitor walletMonitor = mock(IWalletMonitor.class);
        when(walletMonitor.getWallet(any())).thenReturn(TEN);

        when(monitor.getWalletMonitor()).thenReturn(walletMonitor);
        when(monitor.getMarketMetadata(p)).thenReturn(new BaseMarketMetadata(ONE.movePointLeft(SCALE), SCALE, ZERO));
        when(monitor.getExchange()).thenReturn(x);
        return monitor;
    }

    protected static <K, V> Map<K, V> map(K key, V value) {
        HashMap<K, V> result = new HashMap<>();
        result.put(key, value);
        return result;
    }

    protected static <K, V> Map<K, V> map(K key1, V value1, K key2, V value2) {
        HashMap<K, V> result = new HashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }

    protected static DealerData data(Exchange openExchange, BigDecimal openPrice, Exchange closeExchange, BigDecimal closePrice) {
        HashMap<Exchange, BigDecimal> result = new HashMap<>();
        result.put(openExchange, openPrice);
        result.put(closeExchange, closePrice);
        return new DealerData(openExchange, closeExchange, result);
    }
}
