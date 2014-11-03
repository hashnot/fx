package com.hashnot.xchange.event;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.marketdata.Ticker;

/**
 * @author Rafał Krupiński
 */
public interface ITickerListener {
    void ticker(Ticker ticker, Exchange source);
}
