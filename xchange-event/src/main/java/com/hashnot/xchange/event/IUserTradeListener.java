package com.hashnot.xchange.event;

import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;

/**
 * @author Rafał Krupiński
 */
public interface IUserTradeListener {
    void trade(LimitOrder monitored, UserTrade trade, LimitOrder current);
}
