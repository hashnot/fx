package com.hashnot.xchange.async.account;

import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.service.polling.PollingAccountService;

import java.math.BigDecimal;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static com.hashnot.xchange.async.impl.AsyncSupport.call;

/**
 * @author Rafał Krupiński
 */
public class AsyncAccountService implements IAsyncAccountService {
    final private Executor executor;
    final private PollingAccountService service;

    public AsyncAccountService(Executor remoteExecutor, PollingAccountService service) {
        executor = remoteExecutor;
        this.service = service;
    }

    @Override
    public Future<AccountInfo> getAccountInfo(Consumer<Future<AccountInfo>> consumer) {
        return call(service::getAccountInfo, consumer, executor);
    }

    @Override
    public void withdrawFunds(String currency, BigDecimal amount, String address, Consumer<Future<String>> consumer) {
        call(() -> service.withdrawFunds(currency, amount, address), consumer, executor);
    }

    @Override
    public void requestDepositAddress(String currency, Consumer<Future<String>> consumer, String... args) {
        call(() -> service.requestDepositAddress(currency, args), consumer, executor);
    }

}
