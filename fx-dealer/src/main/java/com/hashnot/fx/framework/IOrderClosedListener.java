package com.hashnot.fx.framework;

import com.xeiam.xchange.dto.trade.LimitOrder;

/**
 * @author Rafał Krupiński
 */
public interface IOrderClosedListener {
    /**
     * @param currentOrder may be null, if closed
     */
    void orderClosed(LimitOrder openedOrder, LimitOrder currentOrder);
}
