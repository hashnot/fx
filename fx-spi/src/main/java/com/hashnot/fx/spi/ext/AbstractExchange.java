package com.hashnot.fx.spi.ext;

import com.hashnot.fx.util.Numbers;
import com.hashnot.fx.util.OrderBooks;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

/**
 * @author Rafał Krupiński
 */
public abstract class AbstractExchange implements IExchange {
    final private static Logger log = LoggerFactory.getLogger(SimpleExchange.class);
    static private final BigDecimal TWO = new BigDecimal(2);
    final public Map<CurrencyPair, OrderBook> orderBooks = new HashMap<>();
    final public Map<String, BigDecimal> wallet = new HashMap<>();
    protected final BigDecimal limitPriceUnit;
    protected final IFeeService feeService;
    protected final int scale;
    protected final Map<String, BigDecimal> walletUnit;
    protected final Map<String, BigDecimal> minimumOrder;
    protected final BigDecimal tradeAmountUnit;
    protected final Map<String, LimitOrder> openOrders = new HashMap<>();

    public AbstractExchange(IFeeService feeService, Map<String, BigDecimal> walletUnit, Map<String, BigDecimal> minimumOrder, int limitPriceScale, int tradeAmountScale) {
        this.feeService = feeService;
        this.walletUnit = walletUnit;
        this.minimumOrder = minimumOrder != null ? minimumOrder : Collections.emptyMap();
        this.scale = limitPriceScale;

        limitPriceUnit = ONE.movePointLeft(this.scale);
        tradeAmountUnit = ONE.movePointLeft(tradeAmountScale);
    }

    @Override
    public int getScale(CurrencyPair pair) {
        return scale;
    }

    @Override
    public BigDecimal getLimitPriceUnit(CurrencyPair pair) {
        return limitPriceUnit;
    }

    @Override
    public BigDecimal getTradeAmountUnit(CurrencyPair pair) {
        return tradeAmountUnit;
    }

    @Override
    public BigDecimal getWalletUnit(String currency) {
        BigDecimal result = walletUnit.get(currency);
        if (result == null) throw new IllegalArgumentException("No wallet unit for " + currency + " @" + toString());
        return result;
    }

    @Override
    public BigDecimal getMinimumTrade(String currency) {
        return minimumOrder.getOrDefault(currency, ZERO);
    }

    @Override
    public BigDecimal getFeePercent(CurrencyPair pair) {
        return feeService.getFeePercent(pair);
    }

    @Override
    public Map<CurrencyPair, OrderBook> getOrderBooks() {
        return orderBooks;
    }

    @Override
    public BigDecimal getLimit(String currency) {
        return getWallet(currency).multiply(TWO);
    }

    @Override
    public OrderBook getOrderBook(CurrencyPair pair) {
        return orderBooks.get(pair);
    }

    @Override
    public Map<String, BigDecimal> getWallet() {
        return wallet;
    }

    @Override
    public BigDecimal getWallet(String currency) {
        return wallet.getOrDefault(currency, ZERO);
    }

    @Override
    public abstract String toString();

    @Override
    public String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        PollingTradeService tradeService = getPollingTradeService();
        String id = tradeService.placeLimitOrder(limitOrder);
        if (openOrders.containsKey(id))
            log.warn("ID {} present @{}", id, this);
        openOrders.put(id, limitOrder);
        return id;
    }

    @Override
    public boolean cancelOrder(String orderId) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        if (!openOrders.containsKey(orderId))
            throw new ExchangeException("Unknown order " + orderId);
        boolean result = getPollingTradeService().cancelOrder(orderId);
        openOrders.remove(orderId);
        return result;
    }

    @Override
    public void stop() {
        cancelAll();
    }

    public void cancelAll() {
        openOrders.forEach((k, v) -> {
            try {
                if (!cancelOrder(k))
                    log.warn("Unsuccessful cancel {}@{}", k, this);
            } catch (IOException e) {
                log.warn("Error while cancelling");
            }
        });
    }

    @Override
    public void start(ScheduledExecutorService scheduler) {
        try {
            updateWallet();
        } catch (IOException e) {
            log.warn("Error while updating wallet @{}", this, e);
        }
    }

    protected abstract void updateWallet() throws IOException;

    protected void updateWallet(Exchange x) throws IOException {
        AccountInfo accountInfo = x.getPollingAccountService().getAccountInfo();
        for (com.xeiam.xchange.dto.trade.Wallet w : accountInfo.getWallets()) {
            if (Numbers.equals(w.getBalance(), ZERO)) continue;

            String currency = w.getCurrency();
            BigDecimal current = wallet.getOrDefault(currency, ZERO);
            if (current != null && current.equals(w.getBalance()))
                continue;
            wallet.put(currency, w.getBalance());
        }
    }

    @Override
    public boolean updateOrderBook(CurrencyPair orderBookPair, OrderBook orderBook) {
        OrderBooks.removeOverLimit(orderBook, getLimit(orderBookPair.baseSymbol), getLimit(orderBookPair.counterSymbol));
        OrderBook current = getOrderBook(orderBookPair);
        if (current == null || !OrderBooks.equals(current, orderBook)) {
            orderBooks.put(orderBookPair, orderBook);
            OrderBooks.updateNetPrices(this, orderBookPair);
            return true;
        } else
            return false;
    }
}
