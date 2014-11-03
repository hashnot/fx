package com.hashnot.xchange.event;

import com.xeiam.xchange.dto.marketdata.Trades;

/**
 * @author Rafał Krupiński
 */
public interface ITradesListener {
    void trades(Trades trades);
}
