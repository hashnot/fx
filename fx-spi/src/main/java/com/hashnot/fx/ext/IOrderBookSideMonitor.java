package com.hashnot.fx.ext;

/**
 * @author Rafał Krupiński
 */
public interface IOrderBookSideMonitor {
    void addOrderBookSideListener(IOrderBookSideListener listener, MarketSide source);

    void removeOrderBookSideListener(IOrderBookSideListener listener, MarketSide source);
}
