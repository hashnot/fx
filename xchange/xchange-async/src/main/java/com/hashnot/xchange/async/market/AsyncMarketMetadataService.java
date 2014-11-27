package com.hashnot.xchange.async.market;

import com.hashnot.xchange.async.AbstractAsyncService;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;
import com.xeiam.xchange.service.polling.MarketMetadataService;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public class AsyncMarketMetadataService extends AbstractAsyncService implements IAsyncMarketMetadataService {

    final private MarketMetadataService service;

    public AsyncMarketMetadataService(Executor executor, MarketMetadataService service) {
        super(executor);
        this.service = service;
    }

    @Override
    public Future<MarketMetadata> getMarketMetadata(CurrencyPair pair, Consumer<Future<MarketMetadata>> consumer) {
        return call(() -> service.getMarketMetadata(pair), consumer);
    }
}
