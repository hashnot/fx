package com.hashnot.xchange.event.trade;

import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;

public interface ITrackingTradeService {
    void placeLimitOrder(LimitOrder order, IOrderTradesListener listener);

    void placeMarketOrder(MarketOrder order, IOrderTradesListener listener);

    void cancelOrder(String orderId);
}
