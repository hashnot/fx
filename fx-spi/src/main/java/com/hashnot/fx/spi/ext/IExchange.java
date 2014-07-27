package com.hashnot.fx.spi.ext;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Rafał Krupiński
 */
public interface IExchange extends Exchange, IFeeService {
    /**
     * @return scale (number of )
     */
    int getScale(CurrencyPair pair);

    /**
     * @return 10^{@link #getScale(com.xeiam.xchange.currency.CurrencyPair)}
     */
    BigDecimal getMinIncrease(CurrencyPair pair);

    BigDecimal getFeePercent(CurrencyPair pair);

    BigDecimal getWallet(String currency);

    Map<String, BigDecimal> getWallet();

    OrderBook getOrderBook(CurrencyPair pair);

    Map<CurrencyPair, OrderBook> getOrderBooks();

    BigDecimal getLimit(String currency);

    void start(ScheduledExecutorService scheduler);

    void stop();
}
