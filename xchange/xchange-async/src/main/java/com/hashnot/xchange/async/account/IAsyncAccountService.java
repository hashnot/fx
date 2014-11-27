package com.hashnot.xchange.async.account;

import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.dto.account.AccountInfo;

import java.math.BigDecimal;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public interface IAsyncAccountService {

    /**
     * Get account info
     *
     * @param consumer the AccountInfo object, null if some sort of error occurred. Implementers should log the error.
     * @throws com.xeiam.xchange.ExchangeException                     - Indication that the exchange reported some kind of error with the request or response
     * @throws com.xeiam.xchange.NotAvailableFromExchangeException     - Indication that the exchange does not support the requested function or data
     * @throws com.xeiam.xchange.NotYetImplementedForExchangeException - Indication that the exchange supports the requested function or data, but it has not yet been implemented
     */
    Future<AccountInfo> getAccountInfo(Consumer<Future<AccountInfo>> consumer);

    /**
     * Withdraw funds from this account. Allows to withdraw digital currency funds from the exchange account to an external address
     *
     * @param currency The currency to withdraw
     * @param amount   The amount to withdraw
     * @param address  The destination address
     * @param consumer The result of the withdrawal (usually a transaction ID)
     * @throws ExchangeException                     - Indication that the exchange reported some kind of error with the request or response
     * @throws NotAvailableFromExchangeException     - Indication that the exchange does not support the requested function or data
     * @throws NotYetImplementedForExchangeException - Indication that the exchange supports the requested function or data, but it has not yet been implemented
     */
    void withdrawFunds(String currency, BigDecimal amount, String address, Consumer<Future<String>> consumer);

    /**
     * Request a digital currency address to fund this account. Allows to fund the exchange account with digital currency from an external address
     *
     * @param currency The digital currency that corresponds to the desired deposit address.
     * @param consumer the internal deposit address to send funds to
     * @throws ExchangeException                     - Indication that the exchange reported some kind of error with the request or response
     * @throws NotAvailableFromExchangeException     - Indication that the exchange does not support the requested function or data
     * @throws NotYetImplementedForExchangeException - Indication that the exchange supports the requested function or data, but it has not yet been implemented
     */
    void requestDepositAddress(String currency, Consumer<Future<String>> consumer, String... args);

}
