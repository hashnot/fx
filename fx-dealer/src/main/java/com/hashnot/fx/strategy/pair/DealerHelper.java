package com.hashnot.fx.strategy.pair;

import com.hashnot.xchange.event.IExchangeMonitor;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;

import java.math.BigDecimal;

import static com.hashnot.xchange.ext.util.Comparables.lt;
import static com.hashnot.xchange.ext.util.Orders.c;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static java.math.BigDecimal.ONE;

/**
 * @author Rafał Krupiński
 */
public class DealerHelper {
    private static final BigDecimal LOW_LIMIT = new BigDecimal(".001");

    public static BigDecimal applyFeeToWallet(BigDecimal openOutGross, MarketMetadata meta) {
        BigDecimal feeFactor = meta.getOrderFeeFactor();
        return openOutGross.multiply(ONE.subtract(feeFactor));
    }

    public static boolean checkMinima(BigDecimal openAmountActual, MarketMetadata meta) {
        return !(lt(openAmountActual, LOW_LIMIT)
                || lt(openAmountActual, meta.getAmountMinimum())
        );
    }

    public static int getScale(MarketMetadata meta1, MarketMetadata meta2) {
        int s1 = meta1.getPriceScale();
        int s2 = meta2.getPriceScale();
        return Math.min(s1, s2);
    }

    public static boolean hasMinimumMoney(IExchangeMonitor monitor, BigDecimal price, DealerConfig config) {
        BigDecimal outAmount = monitor.getWalletMonitor().getWallet(config.outgoingCurrency());
        BigDecimal amountMinimum = monitor.getMarketMetadata(config.listing).getAmountMinimum();

        BigDecimal baseAmount;
        if (config.side == ASK)
            baseAmount = outAmount;
        else {
            baseAmount = outAmount.divide(price, amountMinimum.scale(), c.getRoundingMode());
        }

        return !lt(baseAmount, amountMinimum);
    }
}
