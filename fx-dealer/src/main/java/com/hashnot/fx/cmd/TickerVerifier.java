package com.hashnot.fx.cmd;

import com.hashnot.fx.framework.IStrategy;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.market.IOrderBookListener;
import com.hashnot.xchange.event.market.ITickerListener;
import com.hashnot.xchange.event.market.OrderBookUpdateEvent;
import com.hashnot.xchange.event.market.TickerEvent;
import com.hashnot.xchange.ext.util.Comparables;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.hashnot.xchange.ext.util.Tickers.getPrice;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;

/**
 * @author Rafał Krupiński
 */
public class TickerVerifier implements IStrategy, ITickerListener, IOrderBookListener {
    final private static Logger log = LoggerFactory.getLogger(TickerVerifier.class);

    private Map<MarketSide, BigDecimal> ticker = new HashMap<>();
    private Map<MarketSide, BigDecimal> best = new HashMap<>();
    private Map<MarketSide, Long> diffStartTime = new HashMap<>();

    @Override
    public void init(Collection<IExchangeMonitor> exchangeMonitors, Collection<CurrencyPair> pairs) throws Exception {
        for (CurrencyPair pair : pairs) {
            for (IExchangeMonitor monitor : exchangeMonitors) {
                monitor.getTickerMonitor().addTickerListener(this, pair);
                monitor.getOrderBookMonitor().addOrderBookListener(this, pair);
            }
        }
    }

    @Override
    public void ticker(TickerEvent evt) {
        ticker(evt, ASK);
        ticker(evt, BID);
    }

    protected void ticker(TickerEvent evt, Order.OrderType type) {
        MarketSide key = new MarketSide(evt.source, evt.ticker.getCurrencyPair(), type);
        ticker.put(key, getPrice(evt.ticker, type));
        verify(key);
    }

    @Override
    public void orderBookChanged(OrderBookUpdateEvent evt) {
        orderBook(evt, ASK);
        orderBook(evt, BID);
    }

    protected void orderBook(OrderBookUpdateEvent evt, Order.OrderType type) {
        MarketSide key = new MarketSide(evt.source, type);
        best.put(key, evt.orderBook.getOrders(type).get(0).getLimitPrice());
        verify(key);
    }

    private void verify(MarketSide key) {
        BigDecimal ticker = this.ticker.get(key);
        BigDecimal best = this.best.get(key);

        if (Comparables.eq(ticker, best)) {
            if (diffStartTime.remove(key) != null)
                log.debug("{} equals", key);
        } else {
            long diff = 0;
            if (ticker != null && best != null) {
                long now = System.currentTimeMillis();
                Long start = diffStartTime.computeIfAbsent(key, (k) -> now);
                diff = now - start;
            }
            log.info("{} differs: {} {} time: {}", key, ticker, best, diff);
        }
    }
}
