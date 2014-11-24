package com.hashnot.fx.strategy.pair;

import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.util.NetPrice;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static com.hashnot.xchange.ext.util.Numbers.Price.isFurther;
import static com.hashnot.xchange.ext.util.Numbers.lt;
import static com.hashnot.xchange.ext.util.Numbers.min;
import static com.hashnot.xchange.ext.util.Orders.*;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public class DealerHelper {
    final private static Logger log = LoggerFactory.getLogger(DealerHelper.class);

    private static final BigDecimal LOW_LIMIT = new BigDecimal(".001");

    public static OrderBinding deal(LimitOrder openOrderTempl, IExchangeMonitor openMonitor, List<LimitOrder> closeOrders, IExchangeMonitor closeMonitor) {

        //after my open order os closed on the open exchange, this money should disappear
        String openOutgoingCur = outgoingCurrency(openOrderTempl);

        String closeOutCur = incomingCurrency(openOrderTempl);

        BigDecimal openOutGross = openMonitor.getWalletMonitor().getWallet(openOutgoingCur);
        CurrencyPair pair = openOrderTempl.getCurrencyPair();
        BigDecimal openOutNet = applyFeeToWallet(openMonitor, openOutGross, pair);


        BigDecimal closeOutGross = closeMonitor.getWalletMonitor().getWallet(closeOutCur);
        BigDecimal closeOutNet = applyFeeToWallet(closeMonitor, closeOutGross, pair);

        log.debug("type: {}, open {}, close {}", openOrderTempl.getType(), openMonitor, closeMonitor);
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


        BigDecimal openAmount = openOutGross;
        if (openOrderTempl.getType() == Order.OrderType.BID)
            openAmount = openOutGross.divide(openOrderTempl.getLimitPrice(), c);

        BigDecimal closeAmount = getCloseAmount(closeOrders, closeOutGross, openOrderTempl, closeMonitor);

        log.debug("open: {}", openAmount);
        log.debug("close: {}", closeAmount);

        BigDecimal openAmountActual = min(openAmount, closeAmount).setScale(getScale(openMonitor, closeMonitor, pair), RoundingMode.FLOOR);

        if (!checkMinima(openAmountActual, openMonitor, pair)) {
            log.debug("Amount {} less than minimum", openAmountActual);
            return null;
        }
        log.info("open for {}", openAmountActual);


        LimitOrder openOrder = LimitOrder.Builder.from(openOrderTempl).tradableAmount(openAmountActual).build();
        NetPrice.updateNetPrice(openOrder, openMonitor);
        return new OrderBinding(openMonitor.getExchange(), closeMonitor.getExchange(), openOrder, closeOrders);
    }

    protected static BigDecimal applyFeeToWallet(IExchangeMonitor openMonitor, BigDecimal openOutGross, CurrencyPair pair) {
        BigDecimal feeFactor = openMonitor.getMarketMetadata(pair).getOrderFeeFactor();
        return openOutGross.multiply(ONE.subtract(feeFactor));
    }

    protected static boolean checkMinima(BigDecimal openAmountActual, IExchangeMonitor openMonitor, CurrencyPair listing) {
        return !(lt(openAmountActual, LOW_LIMIT)
                || lt(openAmountActual, openMonitor.getMarketMetadata(listing).getAmountMinimum())
        );
    }

    private static int getScale(IExchangeMonitor e1, IExchangeMonitor e2, CurrencyPair pair) {
        int s1 = e1.getMarketMetadata(pair).getPriceScale();
        int s2 = e2.getMarketMetadata(pair).getPriceScale();
        return Math.min(s1, s2);
    }

    public static BigDecimal getCloseAmount(List<LimitOrder> closeOrders, LimitOrder openOrder, IExchangeMonitor closeMonitor) {
        String closeOutCur = incomingCurrency(openOrder);
        BigDecimal closeOutGross = closeMonitor.getWalletMonitor().getWallet(closeOutCur);
        BigDecimal closeOutNet = applyFeeToWallet(closeMonitor, closeOutGross, openOrder.getCurrencyPair());

        return getCloseAmount(closeOrders, closeOutNet, openOrder, closeMonitor);
    }

    private static BigDecimal getCloseAmount(List<LimitOrder> closeOrders, BigDecimal amountLimit, LimitOrder openOrder, IExchangeMonitor closeMonitor) {
        if (openOrder.getType() == Order.OrderType.ASK)
            return totalAmountByValue(closeOrders, amountLimit, openOrder.getNetPrice(), closeMonitor);
        else
            return totalAmountByAmount(closeOrders, amountLimit, openOrder.getNetPrice(), closeMonitor);
    }

    private static BigDecimal totalAmountByAmount(List<LimitOrder> orders, BigDecimal amountLimit, BigDecimal netPriceLimit, IExchangeMonitor x) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        Order.OrderType type = Order.OrderType.ASK;
        for (LimitOrder order : orders) {
            assert type != order.getType();

            BigDecimal netPrice = Orders.getNetPrice(order.getLimitPrice(), type, x.getMarketMetadata(order.getCurrencyPair()).getOrderFeeFactor());
            log.debug("order {} net {}", order, netPrice);

            if (isFurther(netPrice, netPriceLimit, order.getType()))
                break;
            BigDecimal newAmount = totalAmount.add(order.getTradableAmount());

            BigDecimal actualAmount;

            if (!lt(newAmount, amountLimit)) {
                actualAmount = amountLimit.subtract(totalAmount);
                totalAmount = totalAmount.add(actualAmount);
                break;
            } else {
                actualAmount = order.getTradableAmount();
                totalAmount = newAmount;
                totalValue = totalValue.add(actualAmount.multiply(order.getLimitPrice(), c));
            }
        }
        return totalAmount;
    }

    static BigDecimal totalAmountByValue(List<LimitOrder> orders, BigDecimal valueLimit, BigDecimal netPriceLimit, IExchangeMonitor x) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        Order.OrderType type = Order.OrderType.BID;
        for (LimitOrder order : orders) {
            assert type != order.getType();

            BigDecimal netPrice = Orders.getNetPrice(order.getLimitPrice(), type, x.getMarketMetadata(order.getCurrencyPair()).getOrderFeeFactor());
            log.debug("order {} net {}", order, netPrice);

            if (isFurther(netPrice, netPriceLimit, order.getType()))
                break;
            BigDecimal curAmount = order.getTradableAmount();
            BigDecimal newAmount = totalAmount.add(curAmount);

            BigDecimal curValue = curAmount.multiply(order.getLimitPrice(), c);
            BigDecimal newValue = totalValue.add(curValue);

            // if >0 then cut amount
            // TODO if =0 then break
            if (!lt(newValue, valueLimit)) {
                curValue = valueLimit.subtract(totalValue);
                curAmount = curValue.divide(order.getLimitPrice(), c);
                totalAmount = totalAmount.add(curAmount);
                break;
            } else {
                totalAmount = newAmount;
                totalValue = newValue;
            }
        }
        return totalAmount;
    }

}
