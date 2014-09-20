package com.hashnot.fx.spi;

import com.xeiam.xchange.dto.trade.LimitOrder;

/**
 * @author Rafał Krupiński
 */
public interface ILimitOrderPlacementListener {
    void limitOrderPlaced(LimitOrder order, String id);
}
