package com.hashnot.fx.framework;

/**
 * @author Rafał Krupiński
 */
public interface ILimitOrderPlacementListener {
    void limitOrderPlaced(OrderEvent orderEvent);

    void orderCanceled(OrderCancelEvent orderCancelEvent);
}
