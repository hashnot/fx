package com.hashnot.xchange.async.market;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import com.xeiam.xchange.service.polling.MarketMetadataService;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static com.hashnot.xchange.async.impl.AsyncSupport.call;

/**
 * @author Rafał Krupiński
 */
public class AsyncMarketMetadataService implements IAsyncMarketMetadataService {
    final private Executor executor;
    final private MarketMetadataService service;

    public AsyncMarketMetadataService(Executor remoteExecutor, MarketMetadataService service) {
        executor = remoteExecutor;
        this.service = service;
    }

    @Override
    public Future<MarketMetadata> getMarketMetadata(CurrencyPair pair, Consumer<Future<MarketMetadata>> consumer) {
        return call(() -> service.getMarketMetadata(pair), consumer, executor);
    }
}
