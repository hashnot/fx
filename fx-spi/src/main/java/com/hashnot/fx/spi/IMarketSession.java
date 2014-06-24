package com.hashnot.fx.spi;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Rafał Krupiński
 */
public interface IMarketSession {
    Set<String> listCurrencies();

    List<Offer> getOffers(String base, String counter);

    Map<String, BigDecimal> getCurrentWallet();

    void close();

    IMarket getMarket();
}
