package com.hashnot.xchange.event.account;

import com.hashnot.xchange.async.account.IAsyncAccountService;
import com.hashnot.xchange.async.impl.AsyncSupport;
import com.hashnot.xchange.async.impl.ListenerCollection;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.account.AccountInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Future;

/**
 * @author Rafał Krupiński
 */
public class AccountInfoMonitor implements IAccountInfoMonitor {
    final private static Logger log = LoggerFactory.getLogger(AccountInfoMonitor.class);

    final protected Exchange source;
    final protected IAsyncAccountService accountService;

    protected ListenerCollection<IAccountInfoListener> listeners = new ListenerCollection<>(this);

    @Override
    public Future<AccountInfo> update() {
        return accountService.getAccountInfo(this::handleResult);
    }

    protected void handleResult(Future<AccountInfo> future) {
        try {
            AccountInfo info = AsyncSupport.get(future);
            listeners.fire(new AccountInfoEvent(info, source), IAccountInfoListener::onAccountInfo);
        } catch (IOException e) {
            log.warn("Error from {}", source, e);
        }
    }

    public AccountInfoMonitor(Exchange source, IAsyncAccountService accountService) {
        this.accountService = accountService;
        this.source = source;
    }

    @Override
    public void addAccountInfoListener(IAccountInfoListener accountInfoListener) {
        listeners.addListener(accountInfoListener);
    }

    @Override
    public void removeAccountInfoListener(IAccountInfoListener accountInfoListener) {
        listeners.removeListener(accountInfoListener);
    }

    @Override
    public String toString() {
        return "AccountInfoMonitor@" + source;
    }
}
