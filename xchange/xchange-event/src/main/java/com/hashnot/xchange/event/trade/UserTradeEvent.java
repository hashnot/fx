package com.hashnot.xchange.event.trade;

import com.hashnot.xchange.ext.event.Event;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.UserTrade;

/**
 * @author Rafał Krupiński
 */
public class UserTradeEvent extends Event<Exchange> {
    public final Order monitored;
    public final Order current;
    public final UserTrade trade;

    public UserTradeEvent(Order monitored, UserTrade trade, Order current, Exchange source) {
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

    @Override
    public String toString() {
        return "UserTradeEvent{" +
                "monitored=" + monitored +
                ", current=" + current +
                ", trade=" + trade +
                '}';
    }
}
