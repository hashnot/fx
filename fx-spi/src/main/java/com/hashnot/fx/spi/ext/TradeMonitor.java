package com.hashnot.fx.spi.ext;

import com.hashnot.fx.spi.ILimitOrderPlacementListener;
import com.hashnot.fx.spi.IOrderListener;
import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.hashnot.fx.util.Orders.withAmount;
import static java.math.BigDecimal.ZERO;

/**
 * timer task, run() works only until there are monitored orders
 *
 * @author Rafał Krupiński
 */
public class TradeMonitor implements Runnable, ILimitOrderPlacementListener {
    final private static Logger log = LoggerFactory.getLogger(TradeMonitor.class);

    final private PollingTradeService pollingTradeService;
    final private IOrderListener orderClosedListener;


    //state

    private final Set<OrderPair> changed = new HashSet<>();
    protected volatile boolean run = false;

    // id -> order
    final private Map<String, OrderPair> orders = new HashMap<>();

    @Override
    public void run() {
        if (!run)
            return;

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

                changed.add(pair);
            }

            for (OrderPair pair : changed) {
                orderClosedListener.trade(pair.original, pair.current);
            }

            synchronized (orders) {
                if (orders.isEmpty())
                    run = false;
            }

        } catch (IOException e) {
            log.error("Error", e);
        } finally {
            changed.clear();
        }
    }

    protected Trades getTradeHistory() throws IOException {
        return pollingTradeService.getTradeHistory();
    }

    @Override
    public void limitOrderPlaced(LimitOrder order, String id) {
        synchronized (orders) {
            orders.put(id, new OrderPair(order, order));
            run = true;
        }
    }

    public TradeMonitor(IExchange exchange, IOrderListener orderClosedListener) {
        this.pollingTradeService = exchange.getPollingTradeService();
        this.orderClosedListener = orderClosedListener;
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

