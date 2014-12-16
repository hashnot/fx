package com.hashnot.xchange.ext.trade;

import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;

/**
 * @author Rafał Krupiński
 */
public interface IOrderPlacementListener {
    void limitOrderPlaced(OrderPlacementEvent<LimitOrder> orderPlacementEvent);

    void marketOrderPlaced(OrderPlacementEvent<MarketOrder> orderPlacementEvent);

    void orderCanceled(OrderCancelEvent orderCancelEvent);
}
