package com.hashnot.xchange.event.impl;

import com.hashnot.xchange.event.ITickerListener;
import com.hashnot.xchange.event.ITickerMonitor;
import com.hashnot.xchange.event.TickerEvent;
import com.hashnot.xchange.event.impl.exec.RunnableScheduler;
import com.hashnot.xchange.ext.util.Numbers;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @author Rafał Krupiński
 */
public class TickerMonitor extends AbstractParametrizedMonitor<CurrencyPair, ITickerListener, TickerEvent> implements ITickerMonitor {
    final private Logger log = LoggerFactory.getLogger(TickerMonitor.class);

    private final Exchange exchange;
    private final Map<CurrencyPair, Ticker> previous = new HashMap<>();

    @Override
    protected TickerEvent getData(CurrencyPair pair) throws IOException {
        log.debug("Getting ticker from {}", exchange);
        Ticker ticker = exchange.getPollingMarketDataService().getTicker(pair);
        log.debug("{}", ticker);

        Ticker prev = previous.put(pair, ticker);
        if (prev == null || !eq(prev, ticker))
            return new TickerEvent(ticker, exchange);
        else
            return null;

    }

    @Override
    protected void notifyListener(ITickerListener listener, TickerEvent evt) {
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

    public TickerMonitor(Exchange exchange, RunnableScheduler scheduler, Executor executor) {
        super(scheduler, executor);
        this.exchange = exchange;
    }

    private static boolean eq(Ticker t1, Ticker t2) {
        assert t1.getCurrencyPair().equals(t2.getCurrencyPair());
        return t1.getTimestamp() == null ? t2.getTimestamp() == null : t1.getTimestamp().equals(t2.getTimestamp())
                && Numbers.eq(t1.getAsk(), t2.getAsk())
                && Numbers.eq(t1.getBid(), t2.getBid());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + exchange;
    }
}
