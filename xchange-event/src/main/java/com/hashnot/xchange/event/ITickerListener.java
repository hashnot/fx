package com.hashnot.xchange.event;

import com.hashnot.xchange.ext.IExchange;
import com.xeiam.xchange.dto.marketdata.Ticker;

/**
 * @author Rafał Krupiński
 */
public interface ITickerListener {
    void ticker(Ticker ticker, IExchange source);
}
