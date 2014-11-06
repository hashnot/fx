package com.hashnot.fx.framework;

import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.hashnot.xchange.ext.util.Numbers.Price.isBetter;
import static com.hashnot.xchange.ext.util.Numbers.lt;
import static com.hashnot.xchange.ext.util.Numbers.min;
import static com.hashnot.xchange.ext.util.Orders.*;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class Simulation {
    final private static Logger log = LoggerFactory.getLogger(Simulation.class);

    final private Map<Exchange, IExchangeMonitor> monitors;

    private static final BigDecimal LOW_LIMIT = new BigDecimal(".001");

    public Simulation(Map<Exchange, IExchangeMonitor> monitors) {
        this.monitors = monitors;
    }

    public OrderUpdateEvent deal(LimitOrder openOrder, Exchange openExchange, List<LimitOrder> closeOrders, Exchange closeExchange) {

        //after my open order os closed on the open exchange, this money should disappear
        String openOutgoingCur = outgoingCurrency(openOrder);

        String closeOutCur = incomingCurrency(openOrder);

        IExchangeMonitor openMonitor = monitors.get(openExchange);
        BigDecimal openOutGross = openMonitor.getWallet(openOutgoingCur);
        CurrencyPair pair = openOrder.getCurrencyPair();
        BigDecimal openOutNet = applyFeeToWallet(openMonitor, openOutGross, pair);

        IExchangeMonitor closeMonitor = monitors.get(closeExchange);
        BigDecimal closeOutGross = closeMonitor.getWallet(closeOutCur);
        BigDecimal closeOutNet = applyFeeToWallet(closeMonitor, closeOutGross, pair);

        log.debug("type: {}, open {}, close {}", openOrder.getType(), openExchange, closeExchange);
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
        if (openOrder.getType() == Order.OrderType.ASK) {
            openAmount = openOutGross;
            closeAmount = totalAmountByValue(closeOrders, closeOutNet, openOrder.getNetPrice(), closeMonitor);
        } else {
            openAmount = openOutGross.divide(openOrder.getLimitPrice(), c);
            closeAmount = totalAmountByAmount(closeOrders, closeOutNet, openOrder.getNetPrice(), closeMonitor);
        }

        log.debug("open: {}", openAmount);
        log.debug("close: {}", closeAmount);

        BigDecimal openAmountActual = min(openAmount, closeAmount).setScale(getScale(openMonitor, closeMonitor, pair), RoundingMode.FLOOR);

        if (!checkMinima(openAmountActual, openMonitor, pair)) {
            log.debug("Amount {} less than minimum", openAmountActual);
            return null;
        }
        log.info("open for {}", openAmountActual);

        return apply(openOrder, openMonitor, closeOrders, openMonitor, openAmountActual);
    }

    protected BigDecimal applyFeeToWallet(IExchangeMonitor openMonitor, BigDecimal openOutGross, CurrencyPair pair) {
        BigDecimal feeFactor = openMonitor.getMarketMetadata(pair).getOrderFeeFactor();
        return openOutGross.multiply(ONE.subtract(feeFactor));
    }

    protected boolean checkMinima(BigDecimal openAmountActual, IExchangeMonitor openMonitor, CurrencyPair listing) {
        return !(lt(openAmountActual, LOW_LIMIT)
                || lt(openAmountActual, openMonitor.getMarketMetadata(listing).getAmountMinimum())
        );
    }

    private static int getScale(IExchangeMonitor e1, IExchangeMonitor e2, CurrencyPair pair) {
        int s1 = e1.getMarketMetadata(pair).getPriceScale();
        int s2 = e2.getMarketMetadata(pair).getPriceScale();
        return Math.min(s1, s2);
    }

    private OrderUpdateEvent apply(LimitOrder openOrderTempl, IExchangeMonitor openMonitor, List<LimitOrder> closeOrders, IExchangeMonitor closeMonitor, BigDecimal openAmount) {
        // TODO BigDecimal price = betterPrice(openOrderTempl, openMonitor);
        LimitOrder openOrder = LimitOrder.Builder.from(openOrderTempl)/*.limitPrice(price)*/.tradableAmount(openAmount).build();
        List<LimitOrder> myCloseOrders = new LinkedList<>();

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

            LimitOrder close = new LimitOrder(revert(openOrderTempl.getType()), amount, openOrderTempl.getCurrencyPair(), null, null, closeOrder.getLimitPrice());
            myCloseOrders.add(close);
            if (last) break;
        }

        return new OrderUpdateEvent(openMonitor.getExchange(), closeMonitor.getExchange(), openOrder, myCloseOrders);
    }

    private BigDecimal betterPrice(LimitOrder openOrderTemplate, IExchangeMonitor openMonitor) {
        return Orders.betterPrice(openOrderTemplate.getLimitPrice(), openMonitor.getMarketMetadata(openOrderTemplate.getCurrencyPair()).getPriceStep(), openOrderTemplate.getType());
    }

    static BigDecimal totalAmountByAmount(List<LimitOrder> orders, BigDecimal amountLimit, BigDecimal netPriceLimit, IExchangeMonitor x) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        Order.OrderType type = revert(orders.get(0).getType());
        for (LimitOrder order : orders) {
            BigDecimal netPrice = Orders.getNetPrice(order.getLimitPrice(), type, x.getMarketMetadata(order.getCurrencyPair()).getOrderFeeFactor());

            if (isBetter(netPrice, netPriceLimit, order.getType()))
                break;
            log.debug("order {}", order);
            BigDecimal newAmount = totalAmount.add(order.getTradableAmount());

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

    static BigDecimal totalAmountByValue(List<LimitOrder> orders, BigDecimal valueLimit, BigDecimal netPriceLimit, IExchangeMonitor x) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        Order.OrderType type = revert(orders.get(0).getType());
        for (LimitOrder order : orders) {
            BigDecimal netPrice = Orders.getNetPrice(order.getLimitPrice(), type, x.getMarketMetadata(order.getCurrencyPair()).getOrderFeeFactor());
            if (isBetter(netPrice, netPriceLimit, order.getType()))
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

}
