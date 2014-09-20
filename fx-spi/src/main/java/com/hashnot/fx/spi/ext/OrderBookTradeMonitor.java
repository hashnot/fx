package com.hashnot.fx.spi.ext;

import com.hashnot.fx.OrderBookUpdateEvent;
import com.hashnot.fx.spi.IOrderBookListener;
import com.hashnot.fx.spi.IOrderListener;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.hashnot.fx.util.Numbers.isEqual;

/**
 * Lifecycle of a monitored order
 * <p/>
 * 1 opened by us -> openOrders
 * 2 received from exchange
 * <p/>
 * 3 amount matches - monitored ->monitoredOrder, monitoredCurrent, monitoredRemaining
 * 4 received change
 * 5 if less than previous value - decrease current value
 * 6 if fully executed - remove
 * <p/>
 * OR
 * <p/>
 * 3 amount doesn't match - enable open order monitor
 * <p/>
 * <p/>
 * In theory we could want to keep two orders at single price, but in different directions.
 * This should be considered an error, because these transactions would be redundant,
 * and lossy because of the fees
 *
 * @author Rafał Krupiński
 */
public class OrderBookTradeMonitor implements IOrderBookListener {
    final private static Logger log = LoggerFactory.getLogger(OrderBookTradeMonitor.class);

    /* price -> my open order, before the confirmation */
    protected final Map<BigDecimal, LimitOrder> openOrders = new HashMap<>();

    /* price -> current order amount*/
    protected final Map<BigDecimal, BigDecimal> monitoredCurrent = new ConcurrentHashMap<>();

    /**
     * price -> original order
     */
    protected final Map<BigDecimal, LimitOrder> monitoredOrder = new ConcurrentHashMap<>();

    protected final List<IOrderListener> tradeListeners = new LinkedList<>();

    /* orders that are not monitorable because we don't know if our order is first of all of its price,*/
    protected final Set<String> notMonitorable = new HashSet<>();


    protected final PollingTradeService tradeService;

    public OrderBookTradeMonitor(PollingTradeService tradeService) {
        this.tradeService = tradeService;
    }

    // TODO make placeLimitOrder call this
    public void afterPlaceLimitOrder(LimitOrder order) {
        openOrders.put(order.getLimitPrice(), order);
    }

    @Override
    public void changed(OrderBookUpdateEvent orderBookUpdateEvent) {
        // noting to compare with
        if (orderBookUpdateEvent.before == null)
            return;

        OrderBook changes = orderBookUpdateEvent.getChanges();

        List<LimitOrder> asks = changes.getAsks();
        List<LimitOrder> bids = changes.getBids();

        if (asks.isEmpty() && bids.isEmpty()) {
            log.warn("No changes!");
            return;
        }

        if (!asks.isEmpty())
            changed(orderBookUpdateEvent, Order.OrderType.ASK);

        if (!bids.isEmpty())
            changed(orderBookUpdateEvent, Order.OrderType.BID);
    }

    private void changed(OrderBookUpdateEvent event, Order.OrderType type) {
        Iterator<LimitOrder> beforeIter = event.before.getOrders(type).iterator();
        Iterator<LimitOrder> afterIter = event.after.getOrders(type).iterator();

        for (LimitOrder change : event.getChanges().getOrders(type)) {
            BigDecimal price = change.getLimitPrice();

            LimitOrder beforeOrder = find(beforeIter, price);
            LimitOrder afterOrder = find(afterIter, price);

            changed(beforeOrder, afterOrder, change, price);
        }
    }

    private void changed(LimitOrder beforeOrder, LimitOrder afterOrder, LimitOrder change, BigDecimal price) {
        LimitOrder monitored = monitoredOrder.get(price);

        if (monitored != null) {

            int amountCmp = monitored.getTradableAmount().compareTo(afterOrder.getTradableAmount());
            if(amountCmp>0){
                addNotMonitorable(afterOrder);
                monitoredOrder.remove(price);
            }

        } else if (openOrders.containsKey(price)) {
            LimitOrder original = openOrders.remove(price);
            int amountCmp = original.getTradableAmount().compareTo(afterOrder.getTradableAmount());
            if (amountCmp > 0) {
                addNotMonitorable(afterOrder);
            } else {
                monitoredCurrent.put(price, afterOrder.getTradableAmount());
                monitoredOrder.put(price, original);

                if (amountCmp < 0)
                    notify(original, afterOrder);
            }
        }

    }

    private void notify(LimitOrder original, LimitOrder afterOrder) {
        for (IOrderListener orderListener : tradeListeners) {
            orderListener.trade(original, afterOrder);
        }
    }


    private void addNotMonitorable(LimitOrder order) {
        notMonitorable.add(order.getId());
        if (notMonitorable.size() == 1) ;
    }

    private LimitOrder find(Iterator<LimitOrder> iter, BigDecimal price) {
        while (iter.hasNext()) {
            LimitOrder next = iter.next();
            if (isEqual(next.getLimitPrice(), price))
                return next;
        }
        return null;
    }

    public void addOrderListener(IOrderListener listener) {
        tradeListeners.add(listener);
    }
}
