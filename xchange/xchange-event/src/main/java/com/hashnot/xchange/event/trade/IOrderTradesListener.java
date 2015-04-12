package com.hashnot.xchange.event.trade;

import com.hashnot.xchange.ext.trade.IOrderPlacementListener;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;

public interface IOrderTradesListener extends IOrderPlacementListener, IUserTradeListener {
    void orderPlacementFailed(LimitOrder order);

    void orderPlacementFailed(MarketOrder order);
}
