package com.hashnot.xchange.event.account;

import com.hashnot.xchange.ext.event.Event;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.account.AccountInfo;

/**
 * @author Rafał Krupiński
 */
public class AccountInfoEvent extends Event<Exchange> {
    final public AccountInfo accountInfo;

    public AccountInfoEvent(AccountInfo accountInfo, Exchange source) {
        super(source);
        this.accountInfo = accountInfo;
    }
}
