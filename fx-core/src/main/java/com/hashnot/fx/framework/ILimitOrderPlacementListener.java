package com.hashnot.fx.framework;

import com.xeiam.xchange.dto.trade.LimitOrder;

/**
 * @author Rafał Krupiński
 */
public interface ILimitOrderPlacementListener {
    void limitOrderPlaced(OrderEvent orderEvent);

    void orderCanceled(OrderCancelEvent orderCancelEvent);
}
