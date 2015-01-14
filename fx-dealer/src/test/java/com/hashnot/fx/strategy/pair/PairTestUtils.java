package com.hashnot.fx.strategy.pair;

import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.account.IWalletMonitor;
import com.hashnot.xchange.ext.util.Maps;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.BaseMarketMetadata;
import org.mockito.MockSettings;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static com.xeiam.xchange.currency.Currencies.BTC;
import static com.xeiam.xchange.currency.Currencies.EUR;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.UNNECESSARY;
import static org.mockito.Mockito.*;

/**
 * @author Rafał Krupiński
 */
public class PairTestUtils {
    protected static final CurrencyPair p = CurrencyPair.BTC_EUR;
    protected static final int SCALE = 5;
    protected static SimpleOrderCloseStrategy orderCloseStrategy = new SimpleOrderCloseStrategy();

    protected static Map<String, BigDecimal> defaultWallet = Maps.keys(BTC, EUR).map(v(20, 2), v(200, 2));

    protected static IExchangeMonitor getExchangeMonitor(Exchange x) {
        return getExchangeMonitor(x, withSettings().defaultAnswer(RETURNS_DEFAULTS), ZERO, defaultWallet);
    }

    protected static IExchangeMonitor getExchangeMonitor(Exchange x, String name, Map<String, BigDecimal> wallet) {
        return getExchangeMonitor(x, withSettings().name(name), ZERO, wallet);
    }

    protected static IExchangeMonitor getExchangeMonitor(Exchange x, String name) {
        return getExchangeMonitor(x, name, defaultWallet);
    }

    protected static IExchangeMonitor getExchangeMonitor(Exchange x, String name, BigDecimal feeFactor) {
        return getExchangeMonitor(x, withSettings().name(name), feeFactor, defaultWallet);
    }

    protected static IExchangeMonitor getExchangeMonitor(Exchange x, MockSettings settings, BigDecimal feeFactor, Map<String, BigDecimal> wallet) {
        IExchangeMonitor monitor = mock(IExchangeMonitor.class, settings);
        IWalletMonitor walletMonitor = mock(IWalletMonitor.class);

        when(walletMonitor.getWallet()).thenReturn(wallet);
        when(walletMonitor.getWallet(EUR)).thenReturn(wallet.get(EUR));
        when(walletMonitor.getWallet(BTC)).thenReturn(wallet.get(BTC));

        when(walletMonitor.getWallet()).thenReturn(wallet);

        when(monitor.getWalletMonitor()).thenReturn(walletMonitor);
        when(monitor.getMarketMetadata(p)).thenReturn(new BaseMarketMetadata(ONE.movePointLeft(SCALE), SCALE));
        when(monitor.getAccountInfo()).thenReturn(new AccountInfo(null, feeFactor, null));
        when(monitor.getExchange()).thenReturn(x);

        IAsyncExchange ax = mock(IAsyncExchange.class);
        when(monitor.getAsyncExchange()).thenReturn(ax);
        when(ax.getTradeService()).thenReturn(mock(IAsyncTradeService.class));
        return monitor;
    }

    protected static DealerData data(IExchangeMonitor openExchange, BigDecimal openPrice, IExchangeMonitor closeExchange, BigDecimal closePrice) {
        HashMap<Exchange, BigDecimal> result = new HashMap<>();
        result.put(openExchange.getExchange(), openPrice);
        result.put(closeExchange.getExchange(), closePrice);
        return new DealerData(openExchange, closeExchange, result);
    }

    public static BigDecimal v(final String s) {
        return new BigDecimal(s);
    }

    public static BigDecimal v(long s) {
        return new BigDecimal(s);
    }

    public static BigDecimal v(long value, int scale) {
        return new BigDecimal(value).setScale(scale, UNNECESSARY);
    }
}
