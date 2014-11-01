package com.hashnot.fx.ext;

/**
 * @author Rafał Krupiński
 */
public interface IBestOfferMonitor {
    void addBestOfferListener(IBestOfferListener listener, MarketSide source);

    void removeBestOfferListener(IBestOfferListener listener, MarketSide source);
}
