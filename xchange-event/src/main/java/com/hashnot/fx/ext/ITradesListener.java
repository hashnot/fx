package com.hashnot.fx.ext;

import com.xeiam.xchange.dto.marketdata.Trades;

/**
 * @author Rafał Krupiński
 */
public interface ITradesListener {
    void trades(Trades trades);
}
