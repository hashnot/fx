package com.hashnot.xchange.event.market.impl;

import com.hashnot.xchange.async.impl.RunnableScheduler;
import com.hashnot.xchange.async.market.IAsyncMarketDataService;
import com.hashnot.xchange.event.AbstractParametrizedMonitor;
import com.hashnot.xchange.event.market.ITickerListener;
import com.hashnot.xchange.event.market.ITickerMonitor;
import com.hashnot.xchange.event.market.TickerEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Ticker;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class TickerMonitor extends AbstractParametrizedMonitor<CurrencyPair, ITickerListener, TickerEvent, Ticker> implements ITickerMonitor, TickerMonitorMBean {
    private final Exchange exchange;
    private final IAsyncMarketDataService marketDataService;

    @Override
    protected void getData(CurrencyPair pair, Consumer<Future<Ticker>> consumer) {
        log.debug("Getting ticker from {}", exchange);
        marketDataService.getTicker(pair, consumer);
    }

    @Override
    protected TickerEvent wrap(CurrencyPair param, Ticker ticker) {
        return ticker == null ? null : new TickerEvent(ticker, exchange);
    }

    @Override
    public void addTickerListener(ITickerListener listener, CurrencyPair pair) {
        listeners.addListener(pair, listener);
    }

    @Override
    public void removeTickerListener(ITickerListener listener, CurrencyPair pair) {
        listeners.removeListener(pair, listener);
    }

    public TickerMonitor(IAsyncMarketDataService marketDataService, Exchange exchange, RunnableScheduler scheduler) {
        super(scheduler, ITickerListener::ticker, exchange.getExchangeSpecification().getExchangeName());
        this.marketDataService = marketDataService;
        this.exchange = exchange;
    }

    @Override
    public Map<String, String> getListeners() {
        return listeners.reportListeners();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + exchange;
    }
}
