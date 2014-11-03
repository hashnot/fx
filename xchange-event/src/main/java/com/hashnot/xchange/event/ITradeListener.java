package com.hashnot.xchange.event;

import com.xeiam.xchange.dto.marketdata.Trade;
import com.xeiam.xchange.dto.trade.LimitOrder;

/**
 * @author Rafał Krupiński
 */
public interface ITradeListener {
    void trade(LimitOrder monitored, Trade trade, LimitOrder current);
}
