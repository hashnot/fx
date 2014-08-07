package com.hashnot.fx.spi;

import com.xeiam.xchange.dto.trade.LimitOrder;

/**
 * @author Rafał Krupiński
 */
public interface IOrderListener {
    void trade(LimitOrder monitored, LimitOrder current);
}
