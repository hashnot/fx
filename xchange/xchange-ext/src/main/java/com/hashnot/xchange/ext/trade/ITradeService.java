package com.hashnot.xchange.ext.trade;

import com.xeiam.xchange.service.polling.PollingTradeService;

/**
 * @author Rafał Krupiński
 */
public interface ITradeService extends PollingTradeService {
    void addLimitOrderPlacedListener(ILimitOrderPlacementListener listener);

    void removeLimitOrderPlacedListener(ILimitOrderPlacementListener listener);

    void cancelAll();
}
