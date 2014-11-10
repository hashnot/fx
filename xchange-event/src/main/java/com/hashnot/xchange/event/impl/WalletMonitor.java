package com.hashnot.xchange.event.impl;

import com.hashnot.xchange.event.IWalletListener;
import com.hashnot.xchange.event.IWalletMonitor;
import com.hashnot.xchange.event.WalletUpdateEvent;
import com.hashnot.xchange.event.impl.exec.RunnableScheduler;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.trade.Wallet;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static com.hashnot.xchange.ext.util.Numbers.BigDecimal.isZero;
import static com.hashnot.xchange.ext.util.Numbers.eq;

/**
 * @author Rafał Krupiński
 */
public class WalletMonitor extends AbstractParametrizedMonitor<String, IWalletListener, WalletUpdateEvent> implements Runnable, IWalletMonitor {
    private final Map<String, BigDecimal> wallet = new HashMap<>();
    private final Map<String, BigDecimal> walletView = Collections.unmodifiableMap(wallet);

    private final Exchange exchange;

    @Override
    public void run() {
        try {
            doUpdate();
        } catch (IOException e) {
            log.warn("Error from {}", exchange, e);
        }
    }

    @Override
    public BigDecimal getWallet(String currency) {
        return wallet.getOrDefault(currency, BigDecimal.ZERO);
    }

    @Override
    public Map<String, BigDecimal> getWallet() {
        return walletView;
    }

    @Override
    public void update() throws ExchangeException {
        try {
            doUpdate();
        } catch (IOException e) {
            throw new ExchangeException("Error", e);
        }
    }

    protected void doUpdate() throws IOException {
        AccountInfo accountInfo = exchange.getPollingAccountService().getAccountInfo();
        for (Wallet wallet : accountInfo.getWallets()) {
            BigDecimal current = wallet.getBalance();
            String currency = wallet.getCurrency();
            BigDecimal previous;
            if (isZero(current))
                previous = this.wallet.remove(currency);
            else
                previous = this.wallet.put(currency, current);
            if ((previous == null && !isZero(current)) || !eq(current, previous))
                callListeners(new WalletUpdateEvent(current, currency, exchange), listeners.get(currency));
        }
    }


    @Override
    protected void callListener(IWalletListener listener, WalletUpdateEvent data) {
        listener.walletUpdate(data);
    }

    @Override
    public void removeWalletListener(IWalletListener listener, String currency) {
        removeListener(listener, currency);
    }

    @Override
    public void addWalletListener(IWalletListener listener, String currency) {
        addListener(listener, currency);
    }

    public WalletMonitor(Exchange exchange, RunnableScheduler scheduler, Executor executor) {
        super(scheduler, executor);
        this.exchange = exchange;
    }

    @Override
    protected WalletUpdateEvent getData(String param) throws IOException {
        return null;
    }
}
