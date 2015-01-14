package com.hashnot.xchange.event.account;

import com.xeiam.xchange.dto.account.AccountInfo;

import java.util.concurrent.Future;

/**
 * On-demand monitor, requires calling update
 *
 * @author Rafał Krupiński
 */
public interface IAccountInfoMonitor {
    void addAccountInfoListener(IAccountInfoListener accountInfoListener);

    void removeAccountInfoListener(IAccountInfoListener accountInfoListener);

    Future<AccountInfo> update();
}
