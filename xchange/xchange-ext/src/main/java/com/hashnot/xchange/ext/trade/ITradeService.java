package com.hashnot.xchange.ext.trade;

import com.xeiam.xchange.service.polling.PollingTradeService;

/**
 * @author Rafał Krupiński
 */
public interface ITradeService extends PollingTradeService {
    void addLimitOrderPlacedListener(IOrderPlacementListener listener);

    void removeLimitOrderPlacedListener(IOrderPlacementListener listener);

    void cancelAll();
}
