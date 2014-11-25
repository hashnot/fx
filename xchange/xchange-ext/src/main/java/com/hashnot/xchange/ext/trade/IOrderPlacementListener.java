package com.hashnot.xchange.ext.trade;

import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;

/**
 * @author Rafał Krupiński
 */
public interface IOrderPlacementListener {
    void limitOrderPlaced(OrderEvent<LimitOrder> orderEvent);

    void marketOrderPlaced(OrderEvent<MarketOrder> orderEvent);

    void orderCanceled(OrderCancelEvent orderCancelEvent);
}
