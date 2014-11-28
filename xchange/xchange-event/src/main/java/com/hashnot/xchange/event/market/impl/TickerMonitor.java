package com.hashnot.xchange.event.market.impl;

import com.hashnot.xchange.async.RunnableScheduler;
import com.hashnot.xchange.async.market.IAsyncMarketDataService;
import com.hashnot.xchange.event.AbstractParametrizedMonitor;
import com.hashnot.xchange.event.market.ITickerListener;
import com.hashnot.xchange.event.market.ITickerMonitor;
import com.hashnot.xchange.event.market.TickerEvent;
import com.hashnot.xchange.ext.util.Numbers;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class TickerMonitor extends AbstractParametrizedMonitor<CurrencyPair, ITickerListener, TickerEvent> implements ITickerMonitor {
    final private Logger log = LoggerFactory.getLogger(TickerMonitor.class);

    private final Exchange exchange;
    private final IAsyncMarketDataService marketDataService;
    private final Map<CurrencyPair, Ticker> previous = new HashMap<>();

    @Override
    protected void getData(CurrencyPair pair, Consumer<TickerEvent> consumer) {
        log.debug("Getting ticker from {}", exchange);
        marketDataService.getTicker(pair, (future) -> {
            try {
                Ticker ticker = future.get();
                log.debug("{}", ticker);

                Ticker prev = previous.put(pair, ticker);
                if (prev == null || !eq(prev, ticker))
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

    public TickerMonitor(IAsyncMarketDataService marketDataService, Exchange exchange, RunnableScheduler scheduler, Executor executor) {
        super(scheduler, executor);
        this.marketDataService = marketDataService;
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
