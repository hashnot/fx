package com.hashnot.fx.ext;

import com.hashnot.fx.spi.ext.IExchange;
import com.xeiam.xchange.dto.marketdata.Ticker;

/**
 * @author Rafał Krupiński
 */
public interface ITickerListener {
    void ticker(Ticker ticker, IExchange source);
}
