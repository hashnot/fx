package com.hashnot.fx.framework;

import com.hashnot.xchange.event.Event;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.UserTrade;

/**
 * @author Rafał Krupiński
 */
public class UserTradeEvent extends Event<Exchange> {
    public final LimitOrder monitored;
    public final LimitOrder current;
    public final UserTrade trade;

    public UserTradeEvent(LimitOrder monitored, UserTrade trade, LimitOrder current, Exchange source) {
        super(source);

        assert monitored != null;
        assert trade != null;

        this.monitored = monitored;
        this.current = current;
        this.trade = trade;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof UserTradeEvent && equals((UserTradeEvent) obj);
    }

    protected boolean equals(UserTradeEvent evt) {
        return monitored.equals(evt.monitored)
                && (current == null ? evt.current == null : current.equals(evt.current))
                && trade.equals(evt.trade);
    }
}
