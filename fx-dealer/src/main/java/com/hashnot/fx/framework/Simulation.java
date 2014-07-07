package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ExchangeCache;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class Simulation {
    final private static Logger log = LoggerFactory.getLogger(Simulation.class);

    MathContext c = new MathContext(8, RoundingMode.HALF_UP);
    final private Map<Exchange, ExchangeCache> context;

    final private Map<Exchange, List<LimitOrder>> batch = new HashMap<>();

    public Simulation(Map<Exchange, ExchangeCache> context) {
        this.context = context;
    }

    public boolean add(LimitOrder order, Exchange x) {
        if (batch.isEmpty())
            throw new IllegalStateException("First call on the Simulation must be add(LimitOrder, Exchange, LimitOrder, Exchange)");
        if (!checkFunds(order, x))
            return false;

        _add(order, x);
        return true;
    }

    public boolean add(LimitOrder o1, Exchange e1, LimitOrder o2, Exchange e2) {
        if (!batch.isEmpty())
            log.warn("Unexpected add on non-empty batch");

        CurrencyPair pair = o1.getCurrencyPair();
        assert pair.equals(o2.getCurrencyPair()) : "transactions must involve single currency pair";

        //ensure different exchanges
        if (e1.equals(e2))
            throw new IllegalArgumentException("Orders must be executed on different exchanges");

        //ensure opposite OrderType
        if (o1.getType() == o2.getType())
            throw new IllegalArgumentException("Orders must have opposite OrderType");

        if (ZERO.equals(o1.getLimitPrice()) || ZERO.equals(o1.getTradableAmount()) || ZERO.equals(o2.getLimitPrice()) || ZERO.equals(o2.getTradableAmount()))
            throw new IllegalArgumentException("No ZERO allowed");

        //ensure profitable
        if (o1.getNetPrice().compareTo(o2.getNetPrice()) * Orders.factor(o1.getType()) < 0) {
            log.debug("Transactions not profitable");
            return false;
        }

        //ensure funds
        if (!checkFunds(o1, e1)) {
            return false;
        } else if (!checkFunds(o2, e2)) {
            return false;
        }

        //execute
        _add(o1, e1);
        _add(o2, e2);

        return true;
    }

    private boolean checkFunds(LimitOrder order, Exchange exchange) {
        Map<String, BigDecimal> wallet = context.get(exchange).wallet;
        String currency = Orders.outgoingCurrency(order);
        BigDecimal current = wallet.get(currency);
        if (current == null || current.equals(ZERO)) {
            log.debug("No funds @{}", exchange);
            return false;
        }

        BigDecimal total = ZERO;
        for (LimitOrder o2 : batch.getOrDefault(exchange, Collections.emptyList())) {
            if (order.getType() != o2.getType())
                throw new IllegalArgumentException("Non-matching OrderType");
            total = total.add(Orders.outgoingAmount(o2));
        }
        boolean result = total.compareTo(current) <= 0;
        if (!result)
            log.debug("Insufficient funds @{}", exchange);
        return result;
    }

    protected void _add(LimitOrder order, Exchange exchange) {
        log.info("open {} @{}", order, exchange);
        batch.computeIfAbsent(exchange, y -> new LinkedList<>()).add(order);
    }

    public void report() {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (Map.Entry<Exchange, ExchangeCache> e : context.entrySet()) {
            log.info("{}", e.getKey());
            for (Map.Entry<String, BigDecimal> f : e.getValue().wallet.entrySet()) {
                String currency = f.getKey();
                BigDecimal amount = f.getValue();
                log.info("{} = {}", currency, amount);
                BigDecimal current = totals.getOrDefault(currency, ZERO);
                totals.put(currency, current.add(amount));
            }
        }
        log.info("=== Totals ===");
        for (Map.Entry<String, BigDecimal> e : totals.entrySet())
            log.info("{} = {}", e.getKey(), e.getValue());
        log.info("=== End ===");
    }

    public Map<Exchange, ExchangeCache> getContext() {
        return context;
    }

    public void apply(Queue<OrderUpdateEvent> outQueue) {
        for (Map.Entry<Exchange, List<LimitOrder>> e : batch.entrySet()) {
            for (LimitOrder order : e.getValue()) {
                outQueue.add(new OrderUpdateEvent(e.getKey(), order));
            }
        }
        batch.clear();
    }
}
