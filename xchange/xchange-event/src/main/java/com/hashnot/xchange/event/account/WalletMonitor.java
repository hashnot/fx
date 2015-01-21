package com.hashnot.xchange.event.account;

import com.google.common.util.concurrent.ForwardingFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.hashnot.xchange.async.impl.AsyncSupport;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.trade.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static com.hashnot.xchange.ext.util.BigDecimals.isZero;
import static com.hashnot.xchange.ext.util.Comparables.eq;

/**
 * @author Rafał Krupiński
 */
public class WalletMonitor implements IWalletMonitor, IAccountInfoListener, WalletMonitorMBean {
    final protected Logger log = LoggerFactory.getLogger(getClass());
    private final Map<String, BigDecimal> wallet = new HashMap<>();
    private final Map<String, BigDecimal> walletView = Collections.unmodifiableMap(wallet);

    private final Exchange exchange;
    final protected IAccountInfoMonitor accountInfoMonitor;

    @Override
    public BigDecimal getWallet(String currency) {
        return wallet.getOrDefault(currency, BigDecimal.ZERO);
    }

    @Override
    public Map<String, BigDecimal> getWallet() {
        return walletView;
    }

    public Future<Map<String, BigDecimal>> update() {
        Future<AccountInfo> updateFuture = accountInfoMonitor.update();
        return new ForwardingFuture<Map<String, BigDecimal>>() {
            @Override
            protected Future<Map<String, BigDecimal>> delegate() {
                SettableFuture<Map<String, BigDecimal>> resultFuture = SettableFuture.create();
                try {
                    resultFuture.set(unwrapWallets(AsyncSupport.get(updateFuture)));
                } catch (IOException e) {
                    log.warn("Error from {}", exchange, e);
                    resultFuture.setException(e);
                }
                return resultFuture;
            }
        };
    }

    @Override
    public void onAccountInfo(AccountInfoEvent evt) {
        List<Wallet> wallets = evt.accountInfo.getWallets();
        for (Wallet wallet : wallets) {
            BigDecimal current = wallet.getBalance();
            String currency = wallet.getCurrency();

            BigDecimal previous = this.wallet.put(currency, current);
            if (!eq(current, previous) && !isZero(current))
                log.info("New wallet amount @{} {} = {}", exchange, currency, current);
        }
    }

    protected Map<String, BigDecimal> unwrapWallets(AccountInfo info) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (Wallet wallet : info.getWallets()) {
            result.put(wallet.getCurrency(), wallet.getBalance());
        }
        return result;
    }

    public WalletMonitor(Exchange exchange, IAccountInfoMonitor accountInfoMonitor) {
        this.accountInfoMonitor = accountInfoMonitor;
        this.exchange = exchange;

        accountInfoMonitor.addAccountInfoListener(this);
    }

    @Override
    public String toString() {
        return "WalletMonitor@" + exchange;
    }
}
