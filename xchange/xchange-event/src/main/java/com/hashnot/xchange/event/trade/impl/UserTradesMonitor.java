package com.hashnot.xchange.event.trade.impl;

import com.hashnot.xchange.async.RunnableScheduler;
import com.hashnot.xchange.event.AbstractParameterLessMonitor;
import com.hashnot.xchange.event.trade.IUserTradesListener;
import com.hashnot.xchange.event.trade.IUserTradesMonitor;
import com.hashnot.xchange.event.trade.UserTradesEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.polling.trade.DefaultTradeHistoryParamsTimeSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.Ordering.natural;

/**
 * @author Rafał Krupiński
 */
public class UserTradesMonitor extends AbstractParameterLessMonitor<IUserTradesListener, UserTradesEvent> implements IUserTradesMonitor, UserTradesMonitorMBean {
    final private Logger log = LoggerFactory.getLogger(UserTradesMonitor.class);

    private final Exchange exchange;
    final private PollingTradeService tradeService;

    private volatile Date previous;

    @Override
    protected UserTradesEvent getData() throws IOException {
        Date now = new Date();

        log.debug("Getting trades from {} since {}", tradeService, previous);
        UserTrades trades = tradeService.getTradeHistory(new DefaultTradeHistoryParamsTimeSpan(previous));
        updatePreviousDate(trades, now);
        return new UserTradesEvent(trades, exchange);
    }

    @Override
    protected void callListener(IUserTradesListener listener, UserTradesEvent data) {
        listener.trades(data);
    }

    protected void updatePreviousDate(UserTrades trades, Date now) {
        List<UserTrade> tradeList = trades.getUserTrades();

        if (tradeList.isEmpty()) {
            previous = now;
            return;
        }

        UserTrade first = tradeList.get(0);
        if (first.getTimestamp() == null) {
            previous = now;
            return;
        }

        Date max;
        if (tradeList.size() == 1) {
            max = first.getTimestamp();
        } else if (Trades.TradeSortType.SortByTimestamp == trades.getTradeSortType()) {
            UserTrade last = tradeList.get(tradeList.size() - 1);
            max = natural().max(last.getTimestamp(), first.getTimestamp());
        } else {
            max = tradeList.stream().map(UserTrade::getTimestamp).collect(Collectors.maxBy(natural())).get();
        }
        previous = previous == null ? max : natural().max(previous, max);
    }

    public UserTradesMonitor(Exchange exchange, RunnableScheduler scheduler) {
        super(scheduler);
        this.exchange = exchange;
        this.tradeService = exchange.getPollingTradeService();
    }

    @Override
    public void addTradesListener(IUserTradesListener listener) {
        addListener(listener);
    }

    @Override
    public void removeTradesListener(IUserTradesListener listener) {
        removeListener(listener);
    }

    @Override
    public String toString() {
        return "UserTradesMonitor@" + exchange;
    }

    @Override
    public Collection<String> getListeners() {
        return listeners.keySet().stream().map(Object::toString).collect(Collectors.toList());
    }
}
