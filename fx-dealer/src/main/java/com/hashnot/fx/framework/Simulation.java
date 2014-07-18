package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ExchangeCache;
import com.hashnot.fx.util.FeeHelper;
import com.hashnot.fx.util.Numbers;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

import static com.hashnot.fx.util.FeeHelper.getFeePercent;
import static com.hashnot.fx.util.Orders.*;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class Simulation {
    final private static Logger log = LoggerFactory.getLogger(Simulation.class);

    final private Map<Exchange, ExchangeCache> context;

    final private List<LimitOrder> openOrders = new ArrayList<>(2 << 4);

    public Simulation(Map<Exchange, ExchangeCache> context) {
        this.context = context;
    }

    public void deal(LimitOrder worst, Exchange worstExchange, List<LimitOrder> closeOrders, Exchange bestExchange) {
        ExchangeCache openExchange = context.get(worstExchange);
        ExchangeCache closeExchange = context.get(bestExchange);

        //after my open order os closed on the worst exchange, this money should disappear
        String openOutgoingCur = outgoingCurrency(worst);

        String closeOutCur = incomingCurrency(worst);

        BigDecimal openOutGross = openExchange.wallet.get(openOutgoingCur);
        BigDecimal openOutNet = FeeHelper.addPercent(openOutGross, getFeePercent(worst.getCurrencyPair(), Order.OrderType.ASK, openExchange.feeService));

        BigDecimal closeOutGross = closeExchange.wallet.get(closeOutCur);
        BigDecimal closeOutNet = FeeHelper.addPercent(closeOutGross, getFeePercent(worst.getCurrencyPair(), Order.OrderType.ASK, closeExchange.feeService));

        log.debug("type: {}", worst.getType());
        log.debug("open  {} {} -> {}", openOutgoingCur, openOutGross, openOutNet);
        log.debug("close {} {} -> {}", closeOutCur, closeOutGross, closeOutNet);


        // limit po stronie open ASK = base wallet
        //                  close/ BID = suma( otwarte ASK-> cena*liczba) < base wallet
        //                  openOut = base
        //                  closeOut = counter
        //                  -- OR --
        //                  open BID = counter wallet/cena
        //                  close/ ASK = suma (otwarte BID -> liczba) < base wallet
        //                  openOut = counter
        //                  closeOut = base


        BigDecimal closeAmount;
        BigDecimal openAmount;
        if (worst.getType() == Order.OrderType.ASK) {
            openAmount = openOutNet;
            closeAmount = totalValue(closeOrders, openOutNet, worst.getNetPrice()).divide(worst.getLimitPrice(),c);
        } else {
            openAmount = openOutNet.divide(worst.getLimitPrice(), c);
            closeAmount = totalAmount(closeOrders, closeOutNet, worst.getNetPrice());
        }

        BigDecimal openAmountActual = Numbers.min(openAmount, closeAmount);

        log.info("open for {}", openAmountActual);

    }

    static BigDecimal totalValue(List<LimitOrder> orders, BigDecimal amountLimit, BigDecimal netPriceLimit) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        for (LimitOrder order : orders) {
            if (compareToNetPrice(order, netPriceLimit) > 0)
                break;
            log.debug("order {}", order);
            totalAmount = totalAmount.add(order.getTradableAmount());
            totalValue = totalValue.add(order.getTradableAmount().multiply(order.getLimitPrice()));
            if (totalAmount.compareTo(amountLimit) >= 0)
                break;
        }
        return totalValue;
    }

    static BigDecimal totalAmount(List<LimitOrder> orders, BigDecimal valueLimit, BigDecimal netPriceLimit) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        for (LimitOrder order : orders) {
            if (compareToNetPrice(order, netPriceLimit) > 0)
                break;
            log.debug("order {}", order);
            totalAmount = totalAmount.add(order.getTradableAmount());
            totalValue = totalValue.add(order.getTradableAmount().multiply(order.getLimitPrice()));
            if (totalValue.compareTo(valueLimit) >= 0)
                break;
        }
        return totalAmount;
    }

    private void validatePair(LimitOrder order, LimitOrder close) {
        if (!order.getCurrencyPair().equals(close.getCurrencyPair()))
            throw new IllegalArgumentException("Invalid CurrencyPair");
    }

    private static void validateSameDirection(LimitOrder o1, LimitOrder o2) {
        if (o1.getType() != o2.getType())
            throw new IllegalArgumentException("Orders in different direction");
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
