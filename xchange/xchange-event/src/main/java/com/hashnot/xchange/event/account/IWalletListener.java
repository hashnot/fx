package com.hashnot.xchange.event.account;

/**
 * @author Rafał Krupiński
 */
public interface IWalletListener {
    void walletUpdate(WalletUpdateEvent walletUpdateEvent);
}
