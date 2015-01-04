package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.IBestOfferListener;
import com.hashnot.fx.framework.IOrderBookSideListener;
import com.hashnot.xchange.event.trade.IUserTradeListener;

import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public interface Listener extends IBestOfferListener, IOrderBookSideListener, IUserTradeListener {
    Consumer<Future<Boolean>> onCancel();

    Consumer<Future<String>> onOpen();
}
