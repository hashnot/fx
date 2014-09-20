package com.hashnot.fx.spi.ext;

import com.hashnot.fx.spi.ILimitOrderPlacementListener;
import com.xeiam.xchange.service.polling.PollingTradeService;

/**
 * @author Rafał Krupiński
 */
public interface ITradeService extends PollingTradeService {
    void addLimitOrderPlacedListener(ILimitOrderPlacementListener listener);

    void removeLimitOrderPlacedListener(ILimitOrderPlacementListener listener);
}
