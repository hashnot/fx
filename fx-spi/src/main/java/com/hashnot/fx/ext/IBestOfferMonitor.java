package com.hashnot.fx.ext;

import com.hashnot.fx.spi.ext.IExchange;

/**
 * @author Rafał Krupiński
 */
public interface IBestOfferMonitor {
    void addBestOfferListener(IBestOfferListener listener, Market market);

    void removeBestOfferListener(IBestOfferListener listener, Market market);
}
