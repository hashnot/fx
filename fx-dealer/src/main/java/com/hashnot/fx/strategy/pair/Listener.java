package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.IBestOfferListener;
import com.hashnot.fx.framework.IOrderBookSideListener;
import com.hashnot.xchange.event.trade.IUserTradeListener;

/**
 * @author Rafał Krupiński
 */
public interface Listener extends IBestOfferListener, IOrderBookSideListener, IUserTradeListener {
}
