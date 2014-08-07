package com.hashnot.fx.spi.ext;

import com.hashnot.fx.spi.IOrderBookListener;
import com.hashnot.fx.spi.ITradeListener;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingAccountService;
import com.xeiam.xchange.service.polling.PollingMarketDataService;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.streaming.ExchangeStreamingConfiguration;
import com.xeiam.xchange.service.streaming.StreamingExchangeService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * @author Rafał Krupiński
 */
public interface IExchange extends IFeeService {
    /**
     * @return scale (number of )
     */
    int getScale(CurrencyPair pair);

    /**
     * @return 10^{@link #getScale(com.xeiam.xchange.currency.CurrencyPair)}
     */
    BigDecimal getLimitPriceUnit(CurrencyPair pair);

    BigDecimal getWalletUnit(String currency);

    BigDecimal getMinimumTrade(String currency);

    BigDecimal getTradeAmountUnit(CurrencyPair pair);

    BigDecimal getFeePercent(CurrencyPair pair);

    BigDecimal getWallet(String currency);

    Map<String, BigDecimal> getWallet();

    OrderBook getOrderBook(CurrencyPair pair);

    PollingMarketDataService getPollingMarketDataService();

    StreamingExchangeService getStreamingExchangeService(ExchangeStreamingConfiguration configuration);

    PollingTradeService getPollingTradeService();

    PollingAccountService getPollingAccountService();

    Map<CurrencyPair, OrderBook> getOrderBooks();

    BigDecimal getLimit(String currency);

    void start();

    String placeLimitOrder(LimitOrder limitOrder) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException;

    boolean cancelOrder(String orderId) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException;

    void stop();

    boolean updateOrderBook(CurrencyPair orderBookPair, OrderBook orderBook);

    void cancelAll();

    void addOrderBookListener(CurrencyPair pair, BigDecimal maxAmount, BigDecimal maxValue, IOrderBookListener orderBookMonitor);

    void removeOrderBookListener(IOrderBookListener orderBookMonitor);

    void addTradeListener(CurrencyPair pair, ITradeListener tradeListener);

    void updateWallet() throws IOException;
}
