package com.hashnot.fx.wm;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.hashnot.fx.spi.ConnectionException;
import com.hashnot.fx.spi.IMarket;
import com.hashnot.fx.spi.IMarketSession;
import com.hashnot.fx.spi.Offer;
import com.hashnot.fx.wm.to.OfferConverter;
import com.hashnot.fx.wm.to.OffersTO;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.convert.Registry;
import org.simpleframework.xml.convert.RegistryStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.util.*;

/**
 * @author Rafał Krupiński
 */
public class WMMarketSession implements IMarketSession {
    private WMMarket parent;
    private WebClient webClient;
    private OfferConverter offerConverter = new OfferConverter();

    public WMMarketSession(WMMarket parent, WebClient webClient) {
        this.parent = parent;
        this.webClient = webClient;
    }

    @Override
    public Set<String> listCurrencies() {
        return null;
    }

    @Override
    public List<Offer> getOffers(String base, String counter) {
        InputStream in;
        try {
            WebRequest req = new WebRequest(new URL("https://www.walutomat.pl/update_offers.php"), HttpMethod.GET);

            req.setRequestParameters(Arrays.asList(new NameValuePair("curr", base + "_" + counter)));
            Page page = webClient.getPage(req);
            in = page.getWebResponse().getContentAsStream();
            Persister persister = new Persister(new AnnotationStrategy());

            OffersTO offers = persister.read(OffersTO.class, in);
            return offerConverter.toOffers(offers, parent);
        } catch (Exception e) {
            throw new ConnectionException(e);
        }

    }

    @Override
    public Map<String, BigDecimal> getCurrentWallet() {
        return null;
    }

    @Override
    public void close() {

    }

    @Override
    public IMarket getMarket() {
        return parent;
    }

}
