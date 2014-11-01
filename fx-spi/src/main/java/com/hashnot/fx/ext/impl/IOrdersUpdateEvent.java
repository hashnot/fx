package com.hashnot.fx.ext.impl;

import com.hashnot.fx.ext.Event;
import com.hashnot.fx.ext.MarketSide;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.util.List;

/**
 * @author Rafał Krupiński
 */
public class IOrdersUpdateEvent extends Event<MarketSide> {
    public final List<LimitOrder> orders;

    public IOrdersUpdateEvent(MarketSide source, List<LimitOrder> orders) {
        super(source);
        this.orders = orders;
    }
}
