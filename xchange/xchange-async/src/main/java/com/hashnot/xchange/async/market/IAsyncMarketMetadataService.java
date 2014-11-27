package com.hashnot.xchange.async.market;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.MarketMetadata;

import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * @author Rafał Krupiński
 */
public interface IAsyncMarketMetadataService {
    Future<MarketMetadata> getMarketMetadata(CurrencyPair pair, Consumer<Future<MarketMetadata>> consumer);
}
