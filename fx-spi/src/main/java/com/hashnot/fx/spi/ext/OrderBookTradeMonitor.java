package com.hashnot.fx.spi.ext;

import com.hashnot.fx.OrderBookUpdateEvent;
import com.hashnot.fx.spi.IOrderBookListener;
import com.hashnot.fx.spi.IOrderListener;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Rafał Krupiński
 */
public class OrderBookTradeMonitor implements IOrderBookListener {
    final private static Logger log = LoggerFactory.getLogger(OrderBookTradeMonitor.class);

    protected final Map<String, LimitOrder> openOrders=new HashMap<>();

    /* my part -> current */
    protected final Map<LimitOrder, LimitOrder> monitoredOrders = new ConcurrentHashMap<>();

    protected final List<IOrderListener> tradeListeners = new LinkedList<>();

    /* orders that are unmonitorable because I don't know if my order is first of its price,*/
    protected final Set<String> notMonitorable = new HashSet<>();


    // TODO make placeLimitOrder call this
    public void afterPlaceLimitOrder(String id, LimitOrder order) {
        openOrders.put(id, order);
    }

    @Override
    public void changed(OrderBookUpdateEvent orderBookUpdateEvent) {
        OrderBook changes = orderBookUpdateEvent.getChanges();

        List<LimitOrder> asks = changes.getAsks();
        List<LimitOrder> bids = changes.getBids();

        if (asks.isEmpty() && bids.isEmpty()) {
            log.warn("No changes!");
            return;
        }

        log.info("{}", asks.get(0).toString());
    }
}
