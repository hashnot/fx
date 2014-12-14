package com.hashnot.xchange.event.market.impl;

import com.google.common.collect.Multimaps;
import com.hashnot.xchange.async.RunnableScheduler;
import com.hashnot.xchange.async.market.IAsyncMarketDataService;
import com.hashnot.xchange.event.AbstractParametrizedMonitor;
import com.hashnot.xchange.event.market.ITickerListener;
import com.hashnot.xchange.event.market.ITickerMonitor;
import com.hashnot.xchange.event.market.TickerEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class TickerMonitor extends AbstractParametrizedMonitor<CurrencyPair, ITickerListener, TickerEvent> implements ITickerMonitor, TickerMonitorMBean {
    final private Logger log = LoggerFactory.getLogger(TickerMonitor.class);

    private final Exchange exchange;
    private final IAsyncMarketDataService marketDataService;

    @Override
    protected void getData(CurrencyPair pair, Consumer<TickerEvent> consumer) {
        log.debug("Getting ticker from {}", exchange);
        marketDataService.getTicker(pair, (future) -> {
            try {
                Ticker ticker = future.get();
                log.debug("{}", ticker);
                consumer.accept(new TickerEvent(ticker, exchange));
            } catch (InterruptedException e) {
                log.warn("Interrupted!", e);
            } catch (ExecutionException e) {
                log.warn("Error from {}", exchange, e.getCause());
            }
        });
    }

    @Override
    protected void callListener(ITickerListener listener, TickerEvent evt) {
        listener.ticker(evt);
    }

    @Override
    public void addTickerListener(ITickerListener listener, CurrencyPair pair) {
        addListener(listener, pair);
    }

    @Override
    public void removeTickerListener(ITickerListener listener, CurrencyPair pair) {
        removeListener(listener, pair);
    }

    public TickerMonitor(IAsyncMarketDataService marketDataService, Exchange exchange, RunnableScheduler scheduler) {
        super(scheduler);
        this.marketDataService = marketDataService;
        this.exchange = exchange;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + exchange;
    }

    @Override
    public Map<String, String> getListeners() {
        Map<String, String> result = new HashMap<>();
        Multimaps.asMap(listeners).forEach((k, v) -> result.put(k.toString(), String.join(" ", v.toString())));
        return result;
    }
}
