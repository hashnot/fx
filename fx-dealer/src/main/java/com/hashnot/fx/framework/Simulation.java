package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ExchangeCache;
import com.hashnot.fx.util.FeeHelper;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

import static com.hashnot.fx.util.FeeHelper.getFeePercent;
import static com.hashnot.fx.util.Orders.c;
import static com.hashnot.fx.util.Orders.revert;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class Simulation {
    final private static Logger log = LoggerFactory.getLogger(Simulation.class);

    final private Map<Exchange, ExchangeCache> context;

    private Exchange openExchange;
    private Exchange closeExchange;

    final private List<LimitOrder> openOrders = new ArrayList<>(2 << 4);
    //final private List<LimitOrder> closeOrders = new ArrayList<>(2 << 4);
    private BigDecimal closePrice;

    public Simulation(Map<Exchange, ExchangeCache> context) {
        this.context = context;
    }

    public void deal(LimitOrder worst, Exchange worstExchange, List<LimitOrder> closeOrders, Exchange bestExchange) {
        /*
        best exchange - close orders
        worst exchange - open order

        if opening ASK{

        }
         */

        ExchangeCache openExchange = context.get(worstExchange);
        ExchangeCache closeExchange = context.get(bestExchange);

        //after my open order os closed on the worst exchange, this money should disappear
        String openOutgoingCur = Orders.outgoingCurrency(worst);

        String closeOutCur = Orders.incomingCurrency(worst);

        BigDecimal openOutGross = openExchange.wallet.get(openOutgoingCur);
        BigDecimal openOutNet = FeeHelper.addPercent(openOutGross, getFeePercent(worst.getCurrencyPair(), Order.OrderType.ASK, openExchange.feeService));

        BigDecimal closeOutGross = closeExchange.wallet.get(closeOutCur);
        BigDecimal closeOutNet = FeeHelper.addPercent(closeOutGross, getFeePercent(worst.getCurrencyPair(), Order.OrderType.ASK, closeExchange.feeService));

        log.debug("{} {} {} -> {}", openOutgoingCur, worst.getType(), openOutGross, openOutNet);
        log.debug("{} {} {} -> {}", closeOutCur, worst.getType(), closeOutGross, closeOutNet);


        BigDecimal availBase;
        BigDecimal availCounter;
        if (worst.getType() == Order.OrderType.ASK) {
            availBase = openOutNet;
            availCounter = closeOutNet;
        } else {
            availBase = closeOutNet;
            availCounter = openOutNet;
        }


        //


        BigDecimal totalBase = ZERO;
        BigDecimal totalCounter = ZERO;

        BigDecimal closeAmount;
        BigDecimal openAmount;
        if(worst.getType() == Order.OrderType.ASK)
        closeAmount = totalAmount(closeOrders, closeOutGross);
        else
        openAmount = totalAmount(closeOrders, openOutGross);

        //BigDecimal counterAmount=totalBase.divide(worst.getLimitPrice(), c);
        if (worst.getType() == Order.OrderType.ASK)
            totalCounter = totalBase.m

        log.info("base   : {} {}", totalBase, availBase);
        log.info("counter: {} {}", totalCounter.stripTrailingZeros(), availCounter);


    }

    static BigDecimal totalValue(List<LimitOrder> orders, BigDecimal amountLimit) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        for (LimitOrder order : orders) {
            totalAmount = totalAmount.add(order.getTradableAmount());
            totalValue = totalValue.add(order.getTradableAmount().multiply(order.getLimitPrice()));
            if (totalAmount.compareTo(amountLimit) >= 0)
                break;
        }
        return totalValue;
    }

    static BigDecimal totalAmount(List<LimitOrder> orders, BigDecimal valueLimit) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        for (LimitOrder order : orders) {
            totalAmount = totalAmount.add(order.getTradableAmount());
            totalValue = totalValue.add(order.getTradableAmount().multiply(order.getLimitPrice()));
            if (totalValue.compareTo(valueLimit) >= 0)
                break;
        }
        return totalAmount;
    }

    public boolean open(LimitOrder order, Exchange x) {
        assert !ZERO.equals(order.getLimitPrice());
        assert order.getNetPrice() != null && !ZERO.equals(order.getNetPrice());
        assert !ZERO.equals(order.getTradableAmount());

        if (!validateOpen(order, x))
            return false;

        openExchange = x;
        return openOrders.add(order);
    }

    protected boolean validateOpen(LimitOrder open, Exchange openExchange) {
        if (closePrice == null)
            log.warn("No closing price");
        else {
            if (closeExchange.equals(openExchange))
                throw new IllegalArgumentException("trying to open and close orders on a single exchange");
        }

        return checkFunds(open, openExchange);
    }

    /**
     * compute the price of our order to open
     */
    public void close(LimitOrder order, Exchange x) {
        assert !ZERO.equals(order.getLimitPrice());
        assert order.getNetPrice() != null && !ZERO.equals(order.getNetPrice());
        assert !ZERO.equals(order.getTradableAmount());

        closeExchange = x;
        // TODO better compute price
        closePrice = order.getLimitPrice();
    }

    private void validatePair(LimitOrder order, LimitOrder close) {
        if (!order.getCurrencyPair().equals(close.getCurrencyPair()))
            throw new IllegalArgumentException("Invalid CurrencyPair");
    }

    private static void validateSameDirection(LimitOrder o1, LimitOrder o2) {
        if (o1.getType() != o2.getType())
            throw new IllegalArgumentException("Orders in different direction");
    }

    private boolean checkFunds(LimitOrder order, Exchange exchange) {
        Map<String, BigDecimal> wallet = context.get(exchange).wallet;
        String currency = Orders.outgoingCurrency(order);
        BigDecimal current = wallet.get(currency);
        if (current == null || current.equals(ZERO)) {
            log.debug("No funds @{}", exchange);
            return false;
        }

        ExchangeCache openExchange = context.get(exchange);
        String incomingCur = Orders.incomingCurrency(order);
        BigDecimal openAmountGross = openExchange.wallet.get(incomingCur);
        BigDecimal openAmountNet = FeeHelper.addPercent(openAmountGross, getFeePercent(order.getCurrencyPair(), Order.OrderType.ASK, openExchange.feeService));

        ExchangeCache closeExchange = context.get(this.closeExchange);
        BigDecimal currentNet = FeeHelper.addPercent(current, getFeePercent(order.getCurrencyPair(), Order.OrderType.ASK, closeExchange.feeService));

        log.debug("{} {} {} -> {}", incomingCur, order.getType(), openAmountGross, openAmountNet);
        log.debug("{} {} {} -> {}", currency, order.getType(), current, currentNet);


        BigDecimal totalOut = ZERO;
        BigDecimal totalIn = ZERO;

        for (LimitOrder o2 : this.openOrders) {
            if (order.getType() != o2.getType())
                throw new IllegalArgumentException("Non-matching OrderType");
            totalOut = totalOut.add(Orders.outgoingAmount(o2));
            totalIn = totalIn.add(Orders.incomingAmount(o2));
        }
        log.info("total in  {} <=> {}", totalIn, openAmountNet);
        log.info("total out {} <=> {}", totalOut, currentNet);
        boolean result = totalOut.compareTo(currentNet) <= 0 && totalIn.compareTo(openAmountNet) <= 0;
        if (!result)
            log.debug("Insufficient funds @{}", exchange);
        return result;
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

    public void clear() {
        openOrders.clear();
        closePrice = null;
    }

/*
    public void apply(Queue<OrderUpdateEvent> outQueue) {
        for (Map.Entry<Exchange, List<LimitOrder>> e : batch.entrySet()) {
            for (LimitOrder order : e.getValue()) {
                outQueue.add(new OrderUpdateEvent(e.getKey(), order));
            }
        }
        batch.clear();
    }
*/
}
