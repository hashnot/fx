package com.hashnot.xchange.ext.util;

import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.Ticker;

import java.math.BigDecimal;

import static com.xeiam.xchange.dto.Order.OrderType.ASK;

/**
 * @author Rafał Krupiński
 */
public class Tickers {
    public static boolean eq(Ticker t1, Ticker t2) {
        assert t1.getCurrencyPair().equals(t2.getCurrencyPair());
        return Numbers.eq(t1.getAsk(), t2.getAsk())
                && Numbers.eq(t1.getBid(), t2.getBid());
    }

    public static BigDecimal getPrice(Ticker ticker, Order.OrderType type) {
        return type == ASK ? ticker.getAsk() : ticker.getBid();
    }
}
