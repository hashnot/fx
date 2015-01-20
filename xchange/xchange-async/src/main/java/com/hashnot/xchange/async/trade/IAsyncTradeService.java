package com.hashnot.xchange.async.trade;

import com.hashnot.xchange.ext.trade.IOrderPlacementListener;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.TradeServiceHelper;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.trade.TradeHistoryParams;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Asynchronous flavour of {@link com.xeiam.xchange.service.polling.PollingTradeService}.
 *
 * @author Rafał Krupiński
 */
public interface IAsyncTradeService {
    void getOpenOrders(Consumer<Future<OpenOrders>> consumer);

    void placeMarketOrder(MarketOrder marketOrder, Consumer<Future<String>> consumer);

    Future<String> placeLimitOrder(LimitOrder limitOrder, Consumer<Future<String>> consumer);

    String placeLimitOrder(LimitOrder limitOrder) throws ExecutionException, InterruptedException, IOException;

    Future<Boolean> cancelOrder(String orderId, Consumer<Future<Boolean>> consumer);

    void getTradeHistory(Consumer<Future<UserTrades>> consumer, Object... arguments);

    void getTradeHistory(TradeHistoryParams params, Consumer<Future<UserTrades>> consumer);

    TradeHistoryParams createTradeHistoryParams();

    void getExchangeSymbols(Consumer<Future<Collection<CurrencyPair>>> consumer);

    void addLimitOrderPlacedListener(IOrderPlacementListener listener);

    void removeLimitOrderPlacedListener(IOrderPlacementListener listener);

    Future<Map<CurrencyPair, ? extends TradeServiceHelper>> getMetadata(Consumer<Future<Map<CurrencyPair, ? extends TradeServiceHelper>>> consumer);

}
