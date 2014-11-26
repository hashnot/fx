package com.hashnot.xchange.ext;

import com.hashnot.xchange.ext.trade.ITradeService;
import com.xeiam.xchange.Exchange;

/**
 * @author Rafał Krupiński
 */
public interface IExchange extends Exchange {
    @Override
    ITradeService getPollingTradeService();

}
