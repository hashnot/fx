package com.hashnot.fx.strategy.pair;

import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.account.IWalletMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.BaseMarketMetadata;
import org.junit.Assert;
import org.mockito.MockSettings;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static java.math.BigDecimal.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Rafał Krupiński
 */
public class PairTestUtils {
    protected static final CurrencyPair p = CurrencyPair.BTC_EUR;
    protected static final int SCALE = 5;
    protected static SimpleOrderCloseStrategy orderCloseStrategy = new SimpleOrderCloseStrategy();

    protected static IExchangeMonitor getExchangeMonitor(Exchange x) {
        return getExchangeMonitor(x, withSettings().defaultAnswer(RETURNS_DEFAULTS));
    }

    protected static IExchangeMonitor getExchangeMonitor(Exchange x, String name) {
        return getExchangeMonitor(x, withSettings().name(name));
    }

    protected static IExchangeMonitor getExchangeMonitor(Exchange x, MockSettings settings) {
        IExchangeMonitor monitor = mock(IExchangeMonitor.class, settings);

        IWalletMonitor walletMonitor = mock(IWalletMonitor.class);
        when(walletMonitor.getWallet(any())).thenReturn(TEN);

        when(monitor.getWalletMonitor()).thenReturn(walletMonitor);
        when(monitor.getMarketMetadata(p)).thenReturn(new BaseMarketMetadata(ONE.movePointLeft(SCALE), SCALE, ZERO));
        when(monitor.getExchange()).thenReturn(x);

        IAsyncExchange ax = mock(IAsyncExchange.class);
        when(monitor.getAsyncExchange()).thenReturn(ax);
        when(ax.getTradeService()).thenReturn(mock(IAsyncTradeService.class));
        return monitor;
    }

    protected static <K, V> Map<K, V> map(K key, V value) {
        HashMap<K, V> result = new HashMap<>();
        result.put(key, value);
        return result;
    }

    protected static <K, V> Map<K, V> map(Collection<K> keys, Collection<V> values) {
        Assert.assertEquals(keys.size(), values.size());
        Map<K, V> result = new HashMap<>(keys.size());
        Iterator<V> vi = values.iterator();
        for (K key : keys) {
            V value = vi.next();
            result.put(key, value);
        }
        return result;
    }

    protected static <K, V> Map<K, V> map(K key1, K key2, V value1, V value2) {
        HashMap<K, V> result = new HashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }

    protected static DealerData data(IExchangeMonitor openExchange, BigDecimal openPrice, IExchangeMonitor closeExchange, BigDecimal closePrice) {
        HashMap<Exchange, BigDecimal> result = new HashMap<>();
        result.put(openExchange.getExchange(), openPrice);
        result.put(closeExchange.getExchange(), closePrice);
        return new DealerData(openExchange, closeExchange, result);
    }
}
