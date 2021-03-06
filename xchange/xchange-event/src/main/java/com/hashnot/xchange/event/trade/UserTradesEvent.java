package com.hashnot.xchange.event.trade;

import com.hashnot.xchange.ext.event.Event;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.UserTrades;

/**
 * @author Rafał Krupiński
 */
public class UserTradesEvent extends Event<Exchange> {
    public final UserTrades userTrades;

    public UserTradesEvent(UserTrades userTrades, Exchange source) {
        super(source);
        this.userTrades = userTrades;
    }
}
