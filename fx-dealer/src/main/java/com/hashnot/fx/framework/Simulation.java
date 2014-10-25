package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.util.Numbers;
import com.hashnot.fx.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.List;

import static com.hashnot.fx.util.Orders.*;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class Simulation {
    final private static Logger log = LoggerFactory.getLogger(Simulation.class);
    private static final BigDecimal LOW_LIMIT = new BigDecimal(".001");

    public OrderUpdateEvent deal(LimitOrder worst, IExchange worstExchange, List<LimitOrder> closeOrders, IExchange bestExchange) {

        //after my open order os closed on the worst exchange, this money should disappear
        String openOutgoingCur = outgoingCurrency(worst);

        String closeOutCur = incomingCurrency(worst);

        BigDecimal openOutGross = worstExchange.getWallet(openOutgoingCur);
        BigDecimal openOutNet = getNetPrice(openOutGross, Order.OrderType.ASK, worstExchange.getMarketMetadata(worst.getCurrencyPair()).getOrderFeeFactor());

        BigDecimal closeOutGross = bestExchange.getWallet(closeOutCur);
        BigDecimal closeOutNet = getNetPrice(closeOutGross, Order.OrderType.ASK, bestExchange.getMarketMetadata(worst.getCurrencyPair()).getOrderFeeFactor());

        log.debug("type: {}, best {}, worst {}", worst.getType(), bestExchange, worstExchange);
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
            openAmount = openOutGross;
            closeAmount = totalAmountByValue(closeOrders, closeOutNet, worst.getNetPrice(), bestExchange);
        } else {
            openAmount = openOutGross.divide(worst.getLimitPrice(), c);
            closeAmount = totalAmountByAmount(closeOrders, closeOutNet, worst.getNetPrice(), bestExchange);
        }

        log.debug("open: {}", openAmount);
        log.debug("close: {}", closeAmount);

        BigDecimal openAmountActual = Numbers.min(openAmount, closeAmount).setScale(getScale(worstExchange, bestExchange, worst.getCurrencyPair()), RoundingMode.FLOOR);

        if (!checkMinima(worst, openAmountActual, worstExchange))
            return null;
        log.info("open for {}", openAmountActual);

        return apply(worst, worstExchange, closeOrders, bestExchange, openAmountActual);
    }

    protected boolean checkMinima(LimitOrder worst, BigDecimal openAmountActual, IExchange worstExchange) {
        return !(openAmountActual.compareTo(LOW_LIMIT) < 0
                || openAmountActual.compareTo(worstExchange.getMarketMetadata(worst.getCurrencyPair()).getAmountMinimum()) < 0
        );
    }

    private static int getScale(IExchange e1, IExchange e2, CurrencyPair pair) {
        int s1 = e1.getMarketMetadata(pair).getPriceScale();
        int s2 = e2.getMarketMetadata(pair).getPriceScale();
        return Math.min(s1, s2);
    }

    private OrderUpdateEvent apply(LimitOrder worst, IExchange worstExchange, List<LimitOrder> closeOrders, IExchange bestExchange, BigDecimal openAmount) {
        //TODO BigDecimal price = betterPrice(worst.getLimitPrice(), worstExchange.getPriceStep(worst.getCurrencyPair()), worst.getType());
        BigDecimal price = worst.getLimitPrice();
        LimitOrder openOrder = new LimitOrder(worst.getType(), openAmount, worst.getCurrencyPair(), null, null, price);
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

            LimitOrder close = new LimitOrder(revert(worst.getType()), amount, worst.getCurrencyPair(), null, null, closeOrder.getLimitPrice());
            myCloseOrders.add(close);
            if (last) break;
        }

        return new OrderUpdateEvent(worstExchange, bestExchange, openOrder, myCloseOrders);
    }

    static BigDecimal totalAmountByAmount(List<LimitOrder> orders, BigDecimal amountLimit, BigDecimal netPriceLimit, IExchange x) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        Order.OrderType type = revert(orders.get(0).getType());
        for (LimitOrder order : orders) {
            BigDecimal netPrice = Orders.getNetPrice(order.getLimitPrice(), type, x.getMarketMetadata(order.getCurrencyPair()).getOrderFeeFactor());

            if (netPrice.compareTo(netPriceLimit) * factor(order.getType()) > 0)
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

    static BigDecimal totalAmountByValue(List<LimitOrder> orders, BigDecimal valueLimit, BigDecimal netPriceLimit, IExchange x) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        Order.OrderType type = revert(orders.get(0).getType());
        for (LimitOrder order : orders) {
            BigDecimal netPrice = Orders.getNetPrice(order.getLimitPrice(), type, x.getMarketMetadata(order.getCurrencyPair()).getOrderFeeFactor());
            if (netPrice.compareTo(netPriceLimit) * factor(order.getType()) > 0)
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
