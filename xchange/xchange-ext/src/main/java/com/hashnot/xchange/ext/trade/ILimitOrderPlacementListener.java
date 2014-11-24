package com.hashnot.xchange.ext.trade;

/**
 * @author Rafał Krupiński
 */
public interface ILimitOrderPlacementListener {
    void limitOrderPlaced(OrderEvent orderEvent);

    void orderCanceled(OrderCancelEvent orderCancelEvent);
}
