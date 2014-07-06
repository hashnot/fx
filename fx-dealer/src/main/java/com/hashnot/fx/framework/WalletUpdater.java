package com.hashnot.fx.framework;

import com.hashnot.fx.spi.ExchangeUpdateEvent;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.account.AccountInfo;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/**
 * @author Rafał Krupiński
 */
public class WalletUpdater implements Runnable {
    final private Exchange exchange;
    final private BlockingQueue<ExchangeUpdateEvent> outQueue;

    public WalletUpdater(Exchange x, BlockingQueue<ExchangeUpdateEvent> outQueue) {
        exchange = x;
        this.outQueue = outQueue;
    }

    @Override
    public void run() {
        try {
            AccountInfo accountInfo = exchange.getPollingAccountService().getAccountInfo();
            ExchangeUpdateEvent event = new ExchangeUpdateEvent(exchange, accountInfo);
            outQueue.offer(event);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
