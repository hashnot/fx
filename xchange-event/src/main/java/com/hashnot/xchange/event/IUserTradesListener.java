package com.hashnot.xchange.event;

import com.xeiam.xchange.dto.trade.UserTrades;

/**
 * @author Rafał Krupiński
 */
public interface IUserTradesListener {
    void trades(UserTradesEvent trades);
}
