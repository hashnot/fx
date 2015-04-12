package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.IBestOfferListener;
import com.hashnot.fx.framework.IOrderBookSideListener;
import com.hashnot.xchange.event.trade.IOrderTradesListener;
import com.hashnot.xchange.event.trade.IUserTradeListener;

import java.util.concurrent.Future;

/**
 * @author Rafał Krupiński
 */
public interface Listener extends IBestOfferListener, IOrderBookSideListener, IUserTradeListener, IOrderTradesListener {
    void onCancel(Future<Boolean> cancelResultFuture);

    void onOpen(Future<String> placeResultFuture);
}
