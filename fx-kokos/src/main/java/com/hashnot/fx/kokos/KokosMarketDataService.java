package com.hashnot.fx.kokos;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.gargoylesoftware.htmlunit.util.StringUtils;
import com.google.gson.Gson;
import com.hashnot.fx.kokos.to.OffersTO;
import com.hashnot.fx.kokos.to.OrderBookConverter;
import com.xeiam.xchange.ExchangeException;
import com.xeiam.xchange.NotAvailableFromExchangeException;
import com.xeiam.xchange.NotYetImplementedForExchangeException;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.marketdata.Trades;
import com.xeiam.xchange.service.polling.PollingMarketDataService;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

import static com.xeiam.xchange.currency.Currencies.*;

/**
 * @author Rafał Krupiński
 */
public class KokosMarketDataService implements PollingMarketDataService {
    private static URL URI_OFFERS = create("https://waluty.kokos.pl/pl/ajax/get-offers");

    private WebClient webClient = new WebClient(BrowserVersion.FIREFOX_24);
    private Gson gson = new Gson();
    private OrderBookConverter orderBookConverter = new OrderBookConverter();


    @Override
    public Ticker getTicker(CurrencyPair currencyPair, Object... args) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        throw new NotAvailableFromExchangeException();
    }

    @Override
    public OrderBook getOrderBook(CurrencyPair currencyPair, Object... args) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        WebResponse bidsRaw = getOrderBookRaw(currencyPair);
        OffersTO bidsTo = gson.fromJson(new InputStreamReader(bidsRaw.getContentAsStream()), OffersTO.class);

        WebResponse asksRaw = getOrderBookRaw(reverse(currencyPair));
        OffersTO asksTo = gson.fromJson(new InputStreamReader(asksRaw.getContentAsStream()), OffersTO.class);
        return orderBookConverter.toOrderBook(bidsTo, asksTo, StringUtils.parseHttpDate(bidsRaw.getResponseHeaderValue("Date")));
    }

    private WebResponse getOrderBookRaw(CurrencyPair currencyPair) throws IOException {
        WebRequest request = new WebRequest(URI_OFFERS, HttpMethod.GET);
        LinkedList<NameValuePair> params = new LinkedList<>();
        params.add(new NameValuePair("p", "1"));
        params.add(new NameValuePair("ci", currencyPair.baseSymbol));
        params.add(new NameValuePair("co", currencyPair.counterSymbol));
        request.setRequestParameters(params);
        Page page = webClient.getPage(request);
        return page.getWebResponse();
    }

    @Override
    public Trades getTrades(CurrencyPair currencyPair, Object... args) throws ExchangeException, NotAvailableFromExchangeException, NotYetImplementedForExchangeException, IOException {
        throw new NotYetImplementedForExchangeException();
    }

    private static Collection currencyPairs = Arrays.asList(
            new CurrencyPair(EUR, PLN),
            new CurrencyPair(USD, PLN),
            new CurrencyPair(GBP, PLN),
            new CurrencyPair(CHF, PLN)
    );


    private static URL create(String spec) {
        try {
            return new URL(spec);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static CurrencyPair reverse(CurrencyPair cp) {
        return new CurrencyPair(cp.counterSymbol, cp.baseSymbol);
    }

    @Override
    public Collection<CurrencyPair> getExchangeSymbols() throws IOException {
        return currencyPairs;
    }
}
