package com.hashnot.xchange.async.account;

import com.hashnot.xchange.async.AbstractAsyncService;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.service.polling.PollingAccountService;

import java.math.BigDecimal;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class AsyncAccountService extends AbstractAsyncService implements IAsyncAccountService {
    final private PollingAccountService service;

    public AsyncAccountService(Executor remoteExecutor, PollingAccountService service, Executor consumerExecutor) {
        super(remoteExecutor, consumerExecutor);
        this.service = service;
    }

    @Override
    public Future<AccountInfo> getAccountInfo(Consumer<Future<AccountInfo>> consumer) {
        return call(service::getAccountInfo, consumer);
    }

    @Override
    public void withdrawFunds(String currency, BigDecimal amount, String address, Consumer<Future<String>> consumer) {
        call(() -> service.withdrawFunds(currency, amount, address), consumer);
    }

    @Override
    public void requestDepositAddress(String currency, Consumer<Future<String>> consumer, String... args) {
        call(() -> service.requestDepositAddress(currency, args), consumer);
    }
}
