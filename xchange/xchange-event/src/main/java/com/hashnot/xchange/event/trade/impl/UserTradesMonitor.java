package com.hashnot.xchange.event.trade.impl;

import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.impl.RunnableScheduler;
import com.hashnot.xchange.event.AbstractParameterLessMonitor;
import com.hashnot.xchange.event.trade.IUserTradesListener;
import com.hashnot.xchange.event.trade.IUserTradesMonitor;
import com.hashnot.xchange.event.trade.UserTradesEvent;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.UserTrade;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.trade.params.DefaultTradeHistoryParamsTimeSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.common.collect.Ordering.natural;

/**
 * @author Rafał Krupiński
 */
public class UserTradesMonitor extends AbstractParameterLessMonitor<IUserTradesListener, UserTradesEvent, UserTrades> implements IUserTradesMonitor, UserTradesMonitorMBean {
    final private Logger log = LoggerFactory.getLogger(UserTradesMonitor.class);

    final protected IAsyncExchange exchange;

    private volatile Date lastLocal;
    private volatile Date lastRemote;

    @Override
    protected void getData(Consumer<Future<UserTrades>> consumer) {
        Date previous = lastRemote != null ? lastRemote : lastLocal;
        log.debug("Getting trades from {} since {}", exchange, previous);
        exchange.getTradeService().getTradeHistory(new DefaultTradeHistoryParamsTimeSpan(previous), consumer);
        lastLocal = new Date();
    }

    @Override
    protected UserTradesEvent wrap(UserTrades trades) {
        updatePreviousDate(trades);
        return new UserTradesEvent(trades, exchange.getExchange());
    }

    protected void updatePreviousDate(UserTrades trades) {
        List<UserTrade> tradeList = trades.getUserTrades();

        if (tradeList.isEmpty())
            return;

        UserTrade first = tradeList.get(0);
        if (first.getTimestamp() == null)
            return;

        Date max;
        if (tradeList.size() == 1) {
            max = first.getTimestamp();
        } else if (Trades.TradeSortType.SortByTimestamp == trades.getTradeSortType()) {
            UserTrade last = tradeList.get(tradeList.size() - 1);
            max = natural().max(last.getTimestamp(), first.getTimestamp());
        } else
            max = tradeList.stream().map(UserTrade::getTimestamp).collect(Collectors.maxBy(natural())).get();

        lastRemote = lastRemote == null ? max : natural().max(lastRemote, max);
    }

    public UserTradesMonitor(IAsyncExchange exchange, RunnableScheduler scheduler) {
        super(scheduler, IUserTradesListener::trades, exchange.toString());
        this.exchange = exchange;
    }

    @Override
    public void addTradesListener(IUserTradesListener listener) {
        listeners.addListener(listener);
    }

    @Override
    public void removeTradesListener(IUserTradesListener listener) {
        listeners.removeListener(listener);
    }

    @Override
    public String toString() {
        return "UserTradesMonitor@" + exchange;
    }

    @Override
    public Collection<String> getListeners() {
        return listeners.reportListeners();
    }
}
