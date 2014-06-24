package com.hashnot.fx.kokos;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.google.gson.Gson;
import com.hashnot.fx.kokos.to.OfferConverter;
import com.hashnot.fx.kokos.to.OffersTO;
import com.hashnot.fx.spi.ConnectionException;
import com.hashnot.fx.spi.IMarket;
import com.hashnot.fx.spi.IMarketSession;
import com.hashnot.fx.spi.Offer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * @author Rafał Krupiński
 */
public class KokosMarketSession implements IMarketSession {
    private static URL URI_OFFERS = create("https://waluty.kokos.pl/pl/ajax/get-offers");

    private WebClient webClient = new WebClient(BrowserVersion.FIREFOX_24);
    private KokosMarket parent;
    private Gson gson = new Gson();

    public KokosMarketSession(KokosMarket parent) {
        this.parent = parent;
    }

    @Override
    public Set<String> listCurrencies() {
        return null;

    }

    @Override
    public List<Offer> getOffers(String base, String counter) {
        try {
            List<Offer> offers = doGetOffers(base, counter);
            offers.addAll(doGetOffers(counter, base));
            return offers;
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    private List<Offer> doGetOffers(String base, String counter) throws IOException {
        WebRequest request = new WebRequest(URI_OFFERS, HttpMethod.GET);
        LinkedList<NameValuePair> params = new LinkedList<>();
        params.add(new NameValuePair("p", "1"));
        params.add(new NameValuePair("ci", base));
        params.add(new NameValuePair("co", counter));
        request.setRequestParameters(params);
        Page page = webClient.getPage(request);
        OffersTO offers = gson.fromJson(new InputStreamReader(page.getWebResponse().getContentAsStream()), OffersTO.class);
        OfferConverter offerConverter = new OfferConverter();
        return offerConverter.toOffers(offers, parent);
    }

    @Override
    public Map<String, BigDecimal> getCurrentWallet() {
        return null;
    }

    @Override
    public void close() {
        webClient.closeAllWindows();
    }

    @Override
    public IMarket getMarket() {
        return parent;
    }

    private static URL create(String spec) {
        try {
            return new URL(spec);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
