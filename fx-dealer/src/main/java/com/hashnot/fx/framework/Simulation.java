package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ExchangeCache;
import com.hashnot.fx.spi.ext.IFeeService;
import com.hashnot.fx.util.FeeHelper;
import com.hashnot.fx.util.Numbers;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hashnot.fx.util.FeeHelper.getFeePercent;
import static com.hashnot.fx.util.Orders.*;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class Simulation {
    final private static Logger log = LoggerFactory.getLogger(Simulation.class);
    private static final BigDecimal LOW_LIMIT = new BigDecimal(".001");

    final private Map<Exchange, ExchangeCache> context;

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


        BigDecimal openAmount;
        BigDecimal closeAmount;
        if (worst.getType() == Order.OrderType.ASK) {
            openAmount = openOutNet;
            closeAmount = totalAmountByValue(closeOrders, closeOutNet, worst.getNetPrice(), closeExchange.feeService);
        } else {
            openAmount = openOutNet.divide(worst.getLimitPrice(), c);
            closeAmount = totalAmountByAmount(closeOrders, closeOutNet, worst.getNetPrice(), closeExchange.feeService);
        }

        log.debug("open: {}", openAmount);
        log.debug("close: {}", closeAmount);

        BigDecimal openAmountActual = Numbers.min(openAmount, closeAmount);

        if (openAmountActual.compareTo(LOW_LIMIT) < 0) return;
        log.info("open for {}", openAmountActual);

        verify(worst, openExchange, closeOrders, closeExchange, openAmountActual);
    }

    private void verify(LimitOrder worst, ExchangeCache worstExchange, List<LimitOrder> closeOrders, ExchangeCache bestExchange, BigDecimal openAmount) {
        LimitOrder openOrder = new LimitOrder(worst.getType(), openAmount, worst.getCurrencyPair(), null, null, worst.getLimitPrice());
        apply(worstExchange.wallet, openOrder, worstExchange.feeService);

        BigDecimal total = ZERO;
        for (LimitOrder closeOrder : closeOrders) {
            BigDecimal newTotal = total.add(closeOrder.getTradableAmount());

            boolean last = false;
            BigDecimal amount;
            if (newTotal.compareTo(openAmount) > 0) {
                amount = openAmount.subtract(total);
                last = true;
            } else {
                amount = closeOrder.getTradableAmount();
                total = newTotal;
            }

            LimitOrder close = new LimitOrder(revert(worst.getType()), amount, worst.getCurrencyPair(), null, null, closeOrder.getLimitPrice());
            apply(bestExchange.wallet, close, bestExchange.feeService);
            if (last) break;
        }

        worstExchange.verifyWallet();
        bestExchange.verifyWallet();
    }

    static void apply(Map<String, BigDecimal> wallet, LimitOrder order, IFeeService feeService) {
        order.setNetPrice(getNetPrice(order, feeService.getFeePercent(order.getCurrencyPair())));
        String outCurrency = outgoingCurrency(order);
        BigDecimal outBalance = wallet.get(outCurrency).subtract(outgoingAmount(order));
        wallet.put(outCurrency, outBalance);

        String inCurrency = incomingCurrency(order);
        BigDecimal inBalance = wallet.get(inCurrency).add(incomingAmount(order));
        wallet.put(inCurrency, inBalance);
        log.debug("in {} {} out {} {} --- {}",inCurrency, inBalance, outCurrency, outBalance, order);
    }

    static BigDecimal totalAmountByAmount(List<LimitOrder> orders, BigDecimal amountLimit, BigDecimal netPriceLimit, IFeeService feeService) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        for (LimitOrder order : orders) {
            BigDecimal netPrice = Orders.getNetPrice(order.getLimitPrice(), revert(order.getType()), feeService.getFeePercent(order.getCurrencyPair()));

            if (netPrice.compareTo(netPriceLimit) * factor(order.getType()) > 0)
                break;
            log.debug("order {}", order);
            BigDecimal newAmount = totalAmount.add(order.getTradableAmount());
            //totalAmount = ;

            BigDecimal actualAmount;
            boolean last = false;
            if (newAmount.compareTo(amountLimit) > 0) {
                actualAmount = amountLimit.subtract(totalAmount);
                totalAmount = totalAmount.add(actualAmount);
                last = true;
            } else {
                actualAmount = order.getTradableAmount();
                totalAmount = newAmount;
            }

            totalValue = totalValue.add(actualAmount.multiply(order.getLimitPrice(), c));
            if (last)
                break;
        }
        return totalAmount;
    }

    static BigDecimal totalAmountByValue(List<LimitOrder> orders, BigDecimal valueLimit, BigDecimal netPriceLimit, IFeeService feeService) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        for (LimitOrder order : orders) {
            BigDecimal netPrice = Orders.getNetPrice(order.getLimitPrice(), revert(order.getType()), feeService.getFeePercent(order.getCurrencyPair()));
            if (netPrice.compareTo(netPriceLimit)*factor(order.getType()) > 0)
                break;
            log.debug("order {}", order);
            BigDecimal curAmount = order.getTradableAmount();
            BigDecimal newAmount = totalAmount.add(curAmount);

            BigDecimal curValue = curAmount.multiply(order.getLimitPrice(), c);
            BigDecimal newValue = totalValue.add(curValue);

            boolean last;
            // if >0 then cut amount
            // TODO if =0 then break
            if (newValue.compareTo(valueLimit) >= 0) {
                curValue = valueLimit.subtract(totalValue);
                curAmount = curValue.divide(order.getLimitPrice(), c);
                totalAmount = totalAmount.add(curAmount);
                totalValue = totalValue.add(curValue);
                last = true;
            } else {
                totalAmount = newAmount;
                totalValue = newValue;
                last = false;
            }
            if (last) break;
        }
        return totalAmount;
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
