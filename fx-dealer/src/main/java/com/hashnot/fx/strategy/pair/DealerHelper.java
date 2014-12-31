package com.hashnot.fx.strategy.pair;

import com.google.common.collect.Ordering;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static com.hashnot.xchange.ext.util.BigDecimals.TWO;
import static com.hashnot.xchange.ext.util.Comparables.lt;
import static com.hashnot.xchange.ext.util.Orders.c;
import static com.hashnot.xchange.ext.util.Orders.revert;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static java.math.BigDecimal.ONE;
import static java.math.RoundingMode.FLOOR;
import static java.math.RoundingMode.HALF_EVEN;

/**
 * @author Rafał Krupiński
 */
public class DealerHelper {
    final private static Logger log = LoggerFactory.getLogger(DealerHelper.class);

    private static final BigDecimal LOW_LIMIT = new BigDecimal(".001");

    public static LimitOrder deal(DealerConfig config, DealerData data, BigDecimal limitPrice, SimpleOrderOpenStrategy orderOpenStrategy) {

        //after my open order is closed at the open exchange, this money should disappear
        String openOutgoingCur = outgoingCurrency(config);

        String closeOutCur = incomingCurrency(config);

        BigDecimal openOutGross = data.getOpenExchange().getWalletMonitor().getWallet(openOutgoingCur);
        BigDecimal closeOutGross = data.getCloseExchange().getWalletMonitor().getWallet(closeOutCur);

        MarketMetadata openMetadata = data.getOpenExchange().getMarketMetadata(config.listing);
        MarketMetadata closeMetadata = data.getCloseExchange().getMarketMetadata(config.listing);

        // adjust amounts by dividing by two and check if it's not below minima
        {
            // amount of base currency
            BigDecimal openBaseAmount = data.getOpenExchange().getWalletMonitor().getWallet(config.listing.baseSymbol);
            BigDecimal closeBaseAmount = data.getCloseExchange().getWalletMonitor().getWallet(config.listing.baseSymbol);

            BigDecimal openBaseHalf = openBaseAmount.divide(TWO, openMetadata.getAmountMinimum().scale(), FLOOR);
            BigDecimal closeBaseHalf = closeBaseAmount.divide(TWO, closeMetadata.getAmountMinimum().scale(), FLOOR);

            BigDecimal openLowLimit = openMetadata.getAmountMinimum();
            BigDecimal closeLowLimit = closeMetadata.getAmountMinimum();

            // if half of the wallet is greater than minimum, open for half, if less, open for full amount but only if it's ASK (arbitrary, avoid wallet collision)
            if (lt(openBaseHalf, openLowLimit) || lt(closeBaseHalf, closeLowLimit)) {
                if (config.side == Order.OrderType.BID) {
                    log.info("Skip BID to avoid wallet collision over BID/ASK");
                    return null;
                }
            } else {
                openOutGross = openOutGross.divide(TWO, openMetadata.getAmountMinimum().scale(), FLOOR);
                closeOutGross = closeOutGross.divide(TWO, closeMetadata.getAmountMinimum().scale(), FLOOR);
            }
        }

        {
            BigDecimal openOutNet = applyFeeToWallet(openOutGross, openMetadata);
            BigDecimal closeOutNet = applyFeeToWallet(closeOutGross, closeMetadata);

            log.debug("type: {}, open {}, close {}", config.side, data.getOpenExchange(), data.getCloseExchange());
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
        if (config.side == Order.OrderType.BID)
            openAmount = openOutGross.divide(limitPrice, openMetadata.getAmountMinimum().scale(), HALF_EVEN);

        BigDecimal closeAmount = data.getCloseAmount(config.side, openAmount, limitPrice);

        log.debug("open: {}", openAmount);
        log.debug("available: {}", closeAmount);
        log.debug("close: {}", closeOutGross);

        BigDecimal openAmountActual = Ordering.natural().min(openAmount, closeAmount, closeOutGross).setScale(getScale(openMetadata, closeMetadata), FLOOR);

        if (!checkMinima(openAmountActual, openMetadata)) {
            log.debug("Amount {} less than minimum", openAmountActual);
            return null;
        }

        openAmountActual = orderOpenStrategy.getAmount(openAmountActual, closeMetadata.getAmountMinimum());
        return new LimitOrder.Builder(config.side, config.listing).limitPrice(limitPrice).tradableAmount(openAmountActual).build();
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

    public static boolean hasMinimumMoney(IExchangeMonitor monitor, BigDecimal price, DealerConfig config) {
        BigDecimal outAmount = monitor.getWalletMonitor().getWallet(outgoingCurrency(config));
        BigDecimal amountMinimum = monitor.getMarketMetadata(config.listing).getAmountMinimum();

        BigDecimal baseAmount;
        if (config.side == ASK)
            baseAmount = outAmount;
        else {
            baseAmount = outAmount.divide(price, amountMinimum.scale(), c.getRoundingMode());
        }

        return !lt(baseAmount, amountMinimum);
    }

    private static String incomingCurrency(DealerConfig config) {
        return incomingCurrency(config.side, config.listing);
    }

    private static String outgoingCurrency(DealerConfig config) {
        return incomingCurrency(revert(config.side), config.listing);
    }

    public static String incomingCurrency(Order.OrderType side, CurrencyPair listing) {
        return side == ASK ? listing.counterSymbol : listing.baseSymbol;
    }

}
