package com.hashnot.xchange.event.impl;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.xchange.event.IUserTradeListener;
import com.hashnot.xchange.event.impl.exec.IExecutorStrategy;
import com.hashnot.xchange.event.impl.exec.IExecutorStrategyFactory;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hashnot.xchange.ext.util.Numbers.eq;

/**
 * @author Rafał Krupiński
 */
public class OpenOrderMonitor implements Runnable {
    final private static Logger log = LoggerFactory.getLogger(OpenOrderMonitor.class);

    final private Exchange parent;
    final private IExecutorStrategy poll;

    private final Multimap<CurrencyPair, IUserTradeListener> tradeListeners = Multimaps.newSetMultimap(new ConcurrentHashMap<>(), HashSet::new);
    private final Map<String, LimitOrder> openOrders;

    public OpenOrderMonitor(IExecutorStrategyFactory executorStrategyFactory, Map<String, LimitOrder> openOrders, Exchange parent) {
        this.openOrders = openOrders;
        this.parent = parent;
        poll = executorStrategyFactory.create(this);
    }

    public void run() {
        try {
            OpenOrders orders = parent.getPollingTradeService().getOpenOrders();
            if (orders == null) return;

            List<LimitOrder> currentOrders = orders.getOpenOrders();
            Map<String, LimitOrder> currentOrdersMap = Maps.uniqueIndex(currentOrders, Order::getId);

            List<String> remove = new LinkedList<>();
            for (Map.Entry<String, LimitOrder> e : openOrders.entrySet()) {
                LimitOrder currentOrder = currentOrdersMap.get(e.getKey());
                LimitOrder storedOrder = e.getValue();
                if (currentOrder != null && eq(currentOrder.getTradableAmount(), storedOrder.getTradableAmount()))
                    continue;

                for (IUserTradeListener tradeListener : tradeListeners.get(storedOrder.getCurrencyPair())) {
                    tradeListener.trade(storedOrder, null, currentOrder);
                }
                if (currentOrder == null) {
                    remove.add(e.getKey());
                }

            }
            remove.forEach(openOrders::remove);

        } catch (IOException e) {
            log.warn("Error getting open orders");
        }
    }

    public void addOrderListener(CurrencyPair pair, IUserTradeListener tradeListener) {
        tradeListeners.put(pair, tradeListener);
        if (!poll.isStarted())
            poll.start();
    }
}
