package com.hashnot.fx.strategy.pair;

import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.trade.IAsyncTradeService;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.account.IWalletMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.BaseMarketMetadata;
import org.mockito.MockSettings;

import java.math.BigDecimal;
import java.util.HashMap;
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
        return getExchangeMonitor(x, withSettings().defaultAnswer(RETURNS_DEFAULTS), ZERO);
    }

    protected static IExchangeMonitor getExchangeMonitor(Exchange x, String name) {
        return getExchangeMonitor(x, withSettings().name(name), ZERO);
    }

    protected static IExchangeMonitor getExchangeMonitor(Exchange x, String name, BigDecimal feeFactor) {
        return getExchangeMonitor(x, withSettings().name(name), feeFactor);
    }

    protected static IExchangeMonitor getExchangeMonitor(Exchange x, MockSettings settings, BigDecimal feeFactor) {
        IExchangeMonitor monitor = mock(IExchangeMonitor.class, settings);

        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> wallet = mock(Map.class);
        when(wallet.get(any(String.class))).thenReturn(TEN);

        IWalletMonitor walletMonitor = mock(IWalletMonitor.class);
        when(walletMonitor.getWallet(any())).thenReturn(TEN);
        when(walletMonitor.getWallet()).thenReturn(wallet);

        when(monitor.getWalletMonitor()).thenReturn(walletMonitor);
        when(monitor.getMarketMetadata(p)).thenReturn(new BaseMarketMetadata(ONE.movePointLeft(SCALE), SCALE, feeFactor));
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
}
