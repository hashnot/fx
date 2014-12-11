package com.hashnot.fx.strategy.pair;

import com.google.common.collect.Ordering;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static com.hashnot.xchange.ext.util.Numbers.Price.isFurther;
import static com.hashnot.xchange.ext.util.Numbers.lt;
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

        //after my open order is closed at the open exchange, this money should disappear
        String openOutgoingCur = outgoingCurrency(openOrderTempl);

        String closeOutCur = incomingCurrency(openOrderTempl);

        BigDecimal openOutGross = openMonitor.getWalletMonitor().getWallet(openOutgoingCur);
        BigDecimal closeOutGross = closeMonitor.getWalletMonitor().getWallet(closeOutCur);

        CurrencyPair pair = openOrderTempl.getCurrencyPair();
        MarketMetadata openMetadata = openMonitor.getMarketMetadata(pair);
        MarketMetadata closeMetadata = closeMonitor.getMarketMetadata(pair);

        // adjust amounts by dividing by two and check if it's not below minima
        {
            // amount of base currency
            BigDecimal openBaseAmount = openMonitor.getWalletMonitor().getWallet(pair.baseSymbol);
            BigDecimal closeBaseAmount = closeMonitor.getWalletMonitor().getWallet(pair.baseSymbol);

            BigDecimal two = new BigDecimal(2);

            BigDecimal openBaseHalf = openBaseAmount.divide(two);
            BigDecimal closeBaseHalf = closeBaseAmount.divide(two);

            BigDecimal openLowLimit = openMetadata.getAmountMinimum();
            BigDecimal closeLowLimit = closeMetadata.getAmountMinimum();

            // if half of the wallet is greater than minimum, open for half, if less, open for full amount but only if it's ASK (arbitrary, avoid wallet collision)
            if (lt(openBaseHalf, openLowLimit) || lt(closeBaseHalf, closeLowLimit)) {
                if (openOrderTempl.getType() == Order.OrderType.BID) {
                    log.info("Skip BID to avoid wallet collision over BID/ASK");
                    return null;
                }
            } else {
                openOutGross = openOutGross.divide(two);
                closeOutGross = closeOutGross.divide(two);
            }
        }

        {
            BigDecimal openOutNet = applyFeeToWallet(openOutGross, openMetadata);
            BigDecimal closeOutNet = applyFeeToWallet(closeOutGross, closeMetadata);

            log.debug("type: {}, open {}, close {}", openOrderTempl.getType(), openMonitor, closeMonitor);
            log.debug("open  {} {} -> {}", openOutgoingCur, openOutGross, openOutNet);
            log.debug("close {} {} -> {}", closeOutCur, closeOutGross, closeOutNet);
        }

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
        log.debug("available: {}", closeAmount);
        log.debug("close: {}", closeOutGross);

        BigDecimal openAmountActual = Ordering.natural().min(openAmount, closeAmount, closeOutGross).setScale(getScale(openMetadata, closeMetadata), RoundingMode.FLOOR);

        if (!checkMinima(openAmountActual, openMetadata)) {
            log.debug("Amount {} less than minimum", openAmountActual);
            return null;
        }
        log.info("open for {}", openAmountActual);


        LimitOrder openOrder = LimitOrder.Builder.from(openOrderTempl).tradableAmount(openAmountActual).build();
        return new OrderBinding(openMonitor.getExchange(), closeMonitor.getExchange(), openOrder, closeOrders);
    }

    protected static BigDecimal applyFeeToWallet(BigDecimal openOutGross, MarketMetadata meta) {
        BigDecimal feeFactor = meta.getOrderFeeFactor();
        return openOutGross.multiply(ONE.subtract(feeFactor));
    }

    protected static boolean checkMinima(BigDecimal openAmountActual, MarketMetadata meta) {
        return !(lt(openAmountActual, LOW_LIMIT)
                || lt(openAmountActual, meta.getAmountMinimum())
        );
    }

    private static int getScale(MarketMetadata meta1, MarketMetadata meta2) {
        int s1 = meta1.getPriceScale();
        int s2 = meta2.getPriceScale();
        return Math.min(s1, s2);
    }

    public static BigDecimal getCloseAmount(List<LimitOrder> closeOrders, LimitOrder openOrder, IExchangeMonitor closeMonitor) {
        String closeOutCur = incomingCurrency(openOrder);
        BigDecimal closeOutGross = closeMonitor.getWalletMonitor().getWallet(closeOutCur);
        BigDecimal closeOutNet = applyFeeToWallet(closeOutGross, closeMonitor.getMarketMetadata(openOrder.getCurrencyPair()));

        return getCloseAmount(closeOrders, closeOutNet, openOrder, closeMonitor);
    }

    private static BigDecimal getCloseAmount(List<LimitOrder> closeOrders, BigDecimal amountLimit, LimitOrder openOrder, IExchangeMonitor closeMonitor) {
        if (openOrder.getType() == Order.OrderType.ASK)
            return totalAmountByValue(closeOrders, amountLimit, openOrder.getLimitPrice(), closeMonitor);
        else
            return totalAmountByAmount(closeOrders, amountLimit, openOrder.getLimitPrice());
    }

    private static BigDecimal totalAmountByAmount(List<LimitOrder> orders, BigDecimal amountLimit, BigDecimal netPriceLimit) {
        BigDecimal totalAmount = ZERO;
        Order.OrderType type = Order.OrderType.ASK;
        for (LimitOrder order : orders) {
            assert type != order.getType();

            log.debug("order {}", order);

            if (isFurther(order.getLimitPrice(), netPriceLimit, order.getType()))
                break;

            totalAmount = totalAmount.add(order.getTradableAmount());

            if (!lt(totalAmount, amountLimit)) {
                break;
            }
        }
        return totalAmount;
    }

    static BigDecimal totalAmountByValue(List<LimitOrder> orders, BigDecimal valueLimit, BigDecimal priceLimit, IExchangeMonitor x) {
        BigDecimal totalValue = ZERO;
        BigDecimal totalAmount = ZERO;
        Order.OrderType type = Order.OrderType.BID;
        for (LimitOrder order : orders) {
            assert type != order.getType();

            BigDecimal netPrice = Orders.getNetPrice(order.getLimitPrice(), type, x.getMarketMetadata(order.getCurrencyPair()).getOrderFeeFactor());
            log.debug("order {} net {}", order, netPrice);

            if (isFurther(order.getLimitPrice(), priceLimit, order.getType()))
                break;
            BigDecimal curAmount = order.getTradableAmount();
            BigDecimal curValue = curAmount.multiply(order.getLimitPrice(), c);
            totalValue = totalValue.add(curValue);

            curAmount = curValue.divide(order.getLimitPrice(), c);
            totalAmount = totalAmount.add(curAmount);

            if (!lt(totalValue, valueLimit)) {
                break;
            }
        }
        return totalAmount;
    }

}
