package com.hashnot.xchange.event;

import com.hashnot.xchange.event.Event;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.marketdata.Ticker;

/**
 * @author Rafał Krupiński
 */
public class TickerEvent extends Event<Exchange> {
    public final Ticker ticker;

    public TickerEvent(Ticker ticker, Exchange source) {
        super(source);
        this.ticker = ticker;
    }
}
