package com.hashnot.xchange.event.account;

import com.google.common.util.concurrent.SettableFuture;
import com.hashnot.xchange.async.account.IAsyncAccountService;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.trade.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.hashnot.xchange.ext.util.BigDecimals.isZero;
import static com.hashnot.xchange.ext.util.Comparables.eq;

/**
 * @author Rafał Krupiński
 */
public class WalletMonitor implements IWalletMonitor, WalletMonitorMBean {
    final protected Logger log = LoggerFactory.getLogger(getClass());
    private final Map<String, BigDecimal> wallet = new HashMap<>();
    private final Map<String, BigDecimal> walletView = Collections.unmodifiableMap(wallet);

    private final Exchange exchange;
    final private IAsyncAccountService accountService;

    @Override
    public BigDecimal getWallet(String currency) {
        return wallet.getOrDefault(currency, BigDecimal.ZERO);
    }

    @Override
    public Map<String, BigDecimal> getWallet() {
        return walletView;
    }

    public Future<Map<String, BigDecimal>> update() {
        SettableFuture<Map<String, BigDecimal>> resultFuture = SettableFuture.create();
        accountService.getAccountInfo((future) -> {
            try {
                AccountInfo accountInfo = future.get();
                List<Wallet> wallets = accountInfo.getWallets();
                for (Wallet wallet : wallets) {
                    BigDecimal current = wallet.getBalance();
                    String currency = wallet.getCurrency();

                    this.wallet.compute(currency, (key, previous) -> {
                        BigDecimal result;
                        if (isZero(current)) {
                            if (previous == null)
                                return null;
                            else
                                result = null;
                        } else {
                            if (eq(current, previous))
                                return current;
                            else
                                result = current;
                        }
                        log.info("New wallet amount @{} {} = {}", exchange, currency, current);
                        return result;
                    });
                }
                resultFuture.set(this.walletView);
            } catch (InterruptedException e) {
                log.warn("Interrupted!", e);
                resultFuture.setException(e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                log.warn("Error from {}", exchange, cause);
                resultFuture.setException(cause);
            }
        });
        return resultFuture;
    }

    public WalletMonitor(Exchange exchange, IAsyncAccountService accountService) {
        this.exchange = exchange;
        this.accountService = accountService;
    }

    @Override
    public String toString() {
        return "WalletMonitor@" + exchange;
    }
}
