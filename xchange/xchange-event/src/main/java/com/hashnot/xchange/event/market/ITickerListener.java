package com.hashnot.xchange.event.market;

/**
 * @author Rafał Krupiński
 */
public interface ITickerListener {
    void ticker(TickerEvent tickerEvent);
}
