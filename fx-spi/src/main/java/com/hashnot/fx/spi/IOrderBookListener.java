package com.hashnot.fx.spi;

import com.xeiam.xchange.dto.trade.LimitOrder;

import java.util.List;

/**
 * @author Rafał Krupiński
 */
public interface IOrderBookListener {
    void changed(List<LimitOrder> orders);
}
