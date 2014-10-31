package com.hashnot.fx.spi.ext;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.fx.ext.ITradeListener;
import com.hashnot.fx.util.exec.IExecutorStrategy;
import com.hashnot.fx.util.exec.IExecutorStrategyFactory;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hashnot.fx.util.Numbers.eq;

/**
 * @author Rafał Krupiński
 */
public class OpenOrderMonitor implements Runnable {
    final private static Logger log = LoggerFactory.getLogger(OpenOrderMonitor.class);

    final private IExchange parent;
    final private IExecutorStrategy poll;

    private final Multimap<CurrencyPair, ITradeListener> tradeListeners = Multimaps.newMultimap(new ConcurrentHashMap<>(), () -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
    private final Map<String, LimitOrder> openOrders;

    public OpenOrderMonitor(IExecutorStrategyFactory executorStrategyFactory, Map<String, LimitOrder> openOrders, IExchange parent) {
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

                for (ITradeListener tradeListener : tradeListeners.get(storedOrder.getCurrencyPair())) {
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

    public void addOrderListener(CurrencyPair pair, ITradeListener tradeListener) {
        tradeListeners.put(pair, tradeListener);
        if (!poll.isStarted())
            poll.start();
    }
}
