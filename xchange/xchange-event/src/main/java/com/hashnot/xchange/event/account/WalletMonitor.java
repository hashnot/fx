package com.hashnot.xchange.event.account;

import com.google.common.collect.Iterables;
import com.hashnot.xchange.async.RunnableScheduler;
import com.hashnot.xchange.async.account.IAsyncAccountService;
import com.hashnot.xchange.event.AbstractParametrizedMonitor;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.trade.Wallet;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static com.hashnot.xchange.ext.util.Numbers.BigDecimal.isZero;
import static com.hashnot.xchange.ext.util.Numbers.eq;

/**
 * @author Rafał Krupiński
 */
public class WalletMonitor extends AbstractParametrizedMonitor<String, IWalletListener, WalletUpdateEvent> implements Runnable, IWalletMonitor {
    private final Map<String, BigDecimal> wallet = new HashMap<>();
    private final Map<String, BigDecimal> walletView = Collections.unmodifiableMap(wallet);

    private final Exchange exchange;
    final private IAsyncAccountService accountService;

    @Override
    public void run() {
        update(Collections.emptyList());
    }

    @Override
    public BigDecimal getWallet(String currency) {
        return wallet.getOrDefault(currency, BigDecimal.ZERO);
    }

    @Override
    public Map<String, BigDecimal> getWallet() {
        return walletView;
    }

    public void update(Iterable<IWalletListener> adHocListeners) {
        accountService.getAccountInfo((future) -> {
            try {
                AccountInfo accountInfo = future.get();
                for (Wallet wallet : accountInfo.getWallets()) {
                    BigDecimal current = wallet.getBalance();
                    String currency = wallet.getCurrency();
                    BigDecimal previous = this.wallet.put(currency, current);

                    if ((previous == null && !isZero(current)) || !eq(current, previous)) {
                        WalletUpdateEvent evt = new WalletUpdateEvent(current, currency, exchange);
                        callListeners(evt, Iterables.concat(listeners.get(currency), adHocListeners));
                    }
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted!", e);
            } catch (ExecutionException e) {
                log.warn("Error from {}", exchange, e.getCause());
            }
        });
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

    public WalletMonitor(Exchange exchange, RunnableScheduler scheduler, Executor executor, IAsyncAccountService accountService) {
        super(scheduler, executor);
        this.exchange = exchange;
        this.accountService = accountService;
    }

    @Override
    protected void getData(String param, Consumer<WalletUpdateEvent> consumer) {
    }
}
