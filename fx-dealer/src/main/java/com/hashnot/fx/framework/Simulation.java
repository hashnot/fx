package com.hashnot.fx.framework;

import com.hashnot.fx.FeeHelper;
import com.hashnot.fx.spi.ExchangeCache;
import com.hashnot.fx.util.CurrencyPairUtil;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public class Simulation {
    final private static Logger log = LoggerFactory.getLogger(Simulation.class);

    MathContext c = new MathContext(8, RoundingMode.HALF_UP);
    final private Map<Exchange, ExchangeCache> context;

    public Simulation(Map<Exchange, ExchangeCache> context) {
        this.context = context;
    }

    public void close(LimitOrder order, Exchange x) {
        LimitOrder close = new LimitOrder(CurrencyPairUtil.revert(order.getType()), order.getTradableAmount(), order.getCurrencyPair(), null, null, order.getLimitPrice());
        log.info("close {} @{}", order, x.getExchangeSpecification().getExchangeName());
        add(close, x);
    }

    public void open(LimitOrder order, Exchange x) {
        log.info("open {} @{}", order, x.getExchangeSpecification().getExchangeName());
        add(order, x);
    }

    public void pair(LimitOrder close, Exchange e1, LimitOrder open, Exchange e2) {
        assert !e1.equals(e2);
        CurrencyPair pair = close.getCurrencyPair();
        assert pair.equals(open.getCurrencyPair());

        Map<String, BigDecimal> w1 = context.get(e1).wallet;
        Map<String, BigDecimal> w2 = context.get(e2).wallet;

        BigDecimal baseAmount = w1.get(pair.baseSymbol).add(w2.get(pair.baseSymbol));
        BigDecimal cntrAmount = w1.get(pair.counterSymbol).add(w2.get(pair.counterSymbol));

        open(open, e2);
        close(close, e1);

        BigDecimal price = close.getLimitPrice();
        BigDecimal baseAmount2 = w1.get(pair.baseSymbol).add(w2.get(pair.baseSymbol));
        BigDecimal cntrAmount2 = w1.get(pair.counterSymbol).add(w2.get(pair.counterSymbol));

        log.info("before {} {}", baseAmount, pair.baseSymbol);
        log.info("before {} {}", cntrAmount, pair.counterSymbol);
        BigDecimal valueBefore = cntrAmount.add(baseAmount.multiply(price, c));
        log.info("estimt {} {}", valueBefore, pair.counterSymbol);

        log.info("after {} {}", baseAmount2, pair.baseSymbol);
        log.info("after {} {}", cntrAmount2, pair.counterSymbol);
        BigDecimal valueAfter = cntrAmount2.add(baseAmount2.multiply(price, c));
        log.info("estimt {} {}", valueAfter, pair.counterSymbol);
        log.info("diffEst {}", valueAfter.subtract(valueBefore).toPlainString());

    }

    public void add(LimitOrder order, Exchange x) {
        BigDecimal amountBase = order.getTradableAmount().negate();
        BigDecimal price = order.getLimitPrice();

        BigDecimal fee = context.get(x).feeService.getFeePercent(order.getCurrencyPair());
        price = FeeHelper.addPercent(price, fee);

        BigDecimal amountCounter = order.getTradableAmount().multiply(price, CurrencyPairUtil.c);

        if (order.getType() == Order.OrderType.BID) {
            amountBase = amountBase.negate();
            amountCounter = amountCounter.negate();
        }

        add(x, order.getCurrencyPair().baseSymbol, amountBase);
        add(x, order.getCurrencyPair().counterSymbol, amountCounter);
    }

    public void add(Exchange x, String currency, BigDecimal amount) {
        Map<String, BigDecimal> wallet = context.get(x).wallet;
        BigDecimal currentAmount = wallet.getOrDefault(currency, BigDecimal.ZERO);
        wallet.put(currency, currentAmount.add(amount));
    }

    public BigDecimal get(Exchange x, String currency) {
        return context.get(x).wallet.get(currency);
    }

    public void report() {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (Map.Entry<Exchange, ExchangeCache> e : context.entrySet()) {
            log.info("{}", e.getKey().getExchangeSpecification().getExchangeName());
            for (Map.Entry<String, BigDecimal> f : e.getValue().wallet.entrySet()) {
                String currency = f.getKey();
                BigDecimal amount = f.getValue();
                log.info("{} = {}", currency, amount);
                BigDecimal current = totals.getOrDefault(currency, BigDecimal.ZERO);
                totals.put(currency, current.add(amount));
            }
        }
        log.info("=== Totals ===");
        for (Map.Entry<String, BigDecimal> e : totals.entrySet())
            log.info("{} = {}", e.getKey(), e.getValue());
        log.info("=== End ===");
    }
}
