package com.hashnot.xchange.event;

/**
 * @author Rafał Krupiński
 */
public interface IWalletListener {
    void walletUpdate(WalletUpdateEvent walletUpdateEvent);
}
