package com.hashnot.fx.framework;

import com.hashnot.xchange.ext.IExchange;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedList;
import java.util.List;

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
    private static final BigDecimal LOW_LIMIT = new BigDecimal(".001");

    public OrderUpdateEvent deal(LimitOrder worst, IExchange worstExchange, List<LimitOrder> closeOrders, IExchange bestExchange) {

        //after my open order os closed on the worst exchange, this money should disappear
        String openOutgoingCur = outgoingCurrency(worst);

        String closeOutCur = incomingCurrency(worst);

        BigDecimal openOutGross = worstExchange.getWallet(openOutgoingCur);
        CurrencyPair pair = worst.getCurrencyPair();
        BigDecimal openOutNet = applyFeeToWallet(worstExchange, openOutGross, pair);

        BigDecimal closeOutGross = bestExchange.getWallet(closeOutCur);
        BigDecimal closeOutNet = applyFeeToWallet(bestExchange, closeOutGross, pair);

        log.debug("type: {}, worst {}, best {}", worst.getType(), worstExchange, bestExchange);
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

        BigDecimal openAmountActual = min(openAmount, closeAmount).setScale(getScale(worstExchange, bestExchange, pair), RoundingMode.FLOOR);

        if (!checkMinima(openAmountActual, worstExchange, pair)) {
            log.debug("Amount {} less than minimum", openAmountActual);
            return null;
        }
        log.info("open for {}", openAmountActual);

        return apply(worst, worstExchange, closeOrders, bestExchange, openAmountActual);
    }

    protected BigDecimal applyFeeToWallet(IExchange worstExchange, BigDecimal openOutGross, CurrencyPair pair) {
        BigDecimal feeFactor = worstExchange.getMarketMetadata(pair).getOrderFeeFactor();
        return openOutGross.multiply(ONE.subtract(feeFactor));
    }

    protected boolean checkMinima(BigDecimal openAmountActual, IExchange worstExchange, CurrencyPair listing) {
        return !(lt(openAmountActual, LOW_LIMIT)
                || lt(openAmountActual, worstExchange.getMarketMetadata(listing).getAmountMinimum())
        );
    }

    private static int getScale(IExchange e1, IExchange e2, CurrencyPair pair) {
        int s1 = e1.getMarketMetadata(pair).getPriceScale();
        int s2 = e2.getMarketMetadata(pair).getPriceScale();
        return Math.min(s1, s2);
    }

    private OrderUpdateEvent apply(LimitOrder worst, IExchange worstExchange, List<LimitOrder> closeOrders, IExchange bestExchange, BigDecimal openAmount) {
        //TODO BigDecimal price = betterPrice(worst.getLimitPrice(), worstExchange.getPriceStep(worst.getCurrencyPair()), worst.getType());
        LimitOrder openOrder = LimitOrder.Builder.from(worst).tradableAmount(openAmount).build();
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

    static BigDecimal totalAmountByValue(List<LimitOrder> orders, BigDecimal valueLimit, BigDecimal netPriceLimit, IExchange x) {
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
