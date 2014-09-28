package com.hashnot.fx.spi.ext;

import com.hashnot.fx.spi.ILimitOrderPlacementListener;
import com.hashnot.fx.spi.ITradeListener;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.hashnot.fx.util.Orders.withAmount;
import static java.math.BigDecimal.ZERO;

/**
 * timer task, run() works only until there are monitored orders
 *
 * @author Rafał Krupiński
 */
public class TradeMonitor implements Runnable, ILimitOrderPlacementListener, ITradeMonitor {
    final private static Logger log = LoggerFactory.getLogger(TradeMonitor.class);

    final private PollingTradeService pollingTradeService;
    final private Set<ITradeListener> tradeListeners = new ConcurrentHashSet<>();


    //state

    private final Map<OrderPair, Trade> changed = new HashMap<>();
    private boolean running = false;

    // id -> order
    final private Map<String, OrderPair> orders = new HashMap<>();
    private final RunnableScheduler scheduler;

    @Override
    public void run() {
        try {
            Trades trades = getTradeHistory();

            for (Trade trade : trades.getTrades()) {
                String id = trade.getOrderId();
                OrderPair pair = orders.get(id);
                if (pair == null) {
                    log.warn("Trade on an unknown order {}", trade.getOrderId());
                    continue;
                }

                BigDecimal currentAmount = pair.current.getTradableAmount().subtract(trade.getTradableAmount());
                int amountCmp = currentAmount.compareTo(ZERO);
                if (amountCmp <= 0) {
                    orders.remove(id);
                    pair.current = null;

                    if (amountCmp < 0)
                        log.warn("Calculated Order has negative amount {}", id);
                } else if (amountCmp > 0) {
                    pair.current = withAmount(pair.current, currentAmount);
                }

                changed.put(pair, trade);
            }

            for (Map.Entry<OrderPair, Trade> e : changed.entrySet()) {
                OrderPair pair = e.getKey();
                Trade trade = e.getValue();
                for (ITradeListener listener : tradeListeners) {
                    listener.trade(pair.original, trade, pair.current);
                }
            }

            updateRunning();

        } catch (IOException e) {
            log.error("Error", e);
        } finally {
            changed.clear();
        }
    }

    private synchronized void updateRunning() {
        if (running) {
            if (orders.isEmpty() || tradeListeners.isEmpty()) {
                scheduler.disable(this);
                running = false;
            }
        } else if (!orders.isEmpty() && !tradeListeners.isEmpty()) {
            scheduler.enable(this);
            running = true;
        }
    }

    protected Trades getTradeHistory() throws IOException {
        return pollingTradeService.getTradeHistory();
    }

    @Override
    public void limitOrderPlaced(LimitOrder order, String id) {
        orders.put(id, new OrderPair(order, order));
        updateRunning();
    }

    public TradeMonitor(IExchange exchange, RunnableScheduler scheduler) {
        this.scheduler = scheduler;
        this.pollingTradeService = exchange.getPollingTradeService();
    }

    @Override
    public void addTradeListener(ITradeListener listener) {
        tradeListeners.add(listener);
        updateRunning();
    }

    @Override
    public void removeTradeListener(ITradeListener listener) {
        tradeListeners.remove(listener);
        updateRunning();
    }

    // not implementing equals/hashcode - use identity
    protected static class OrderPair {
        public final LimitOrder original;
        public LimitOrder current;

        public OrderPair(LimitOrder original, LimitOrder current) {
            this.original = original;
            this.current = current;
        }
    }
}

