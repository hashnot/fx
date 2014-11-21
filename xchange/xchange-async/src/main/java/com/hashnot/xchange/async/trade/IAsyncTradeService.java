package com.hashnot.xchange.async.trade;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.dto.trade.OpenOrders;
import com.xeiam.xchange.dto.trade.UserTrades;
import com.xeiam.xchange.service.polling.PollingTradeService;
import com.xeiam.xchange.service.polling.trade.TradeHistoryParams;

import java.util.Collection;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Asynchronous flavour of {@link PollingTradeService}.
 *
 * @author Rafał Krupiński
 */
public interface IAsyncTradeService {
    void getOpenOrders(Consumer<Future<OpenOrders>> consumer);

    void placeMarketOrder(MarketOrder marketOrder, Consumer<Future<String>> consumer);

    void placeLimitOrder(LimitOrder limitOrder, Consumer<Future<String>> consumer);

    void cancelOrder(String orderId, Consumer<Future<Boolean>> consumer);

    void getTradeHistory(Consumer<Future<UserTrades>> consumer, Object... arguments);

    void getTradeHistory(TradeHistoryParams params, Consumer<Future<UserTrades>> consumer);

    TradeHistoryParams createTradeHistoryParams();

    void getExchangeSymbols(Consumer<Future<Collection<CurrencyPair>>> consumer);

    void updateOrder(String orderId, LimitOrder order, Consumer<Future<String>> consumer);
}
