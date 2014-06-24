package com.hashnot.fx.cf;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.google.gson.Gson;
import com.hashnot.fx.cf.to.MarketContainer;
import com.hashnot.fx.cf.to.MarketplaceItemConverter;
import com.hashnot.fx.cf.ui.PasswordDialog;
import com.hashnot.fx.cf.ui.PasswordFuture;
import com.hashnot.fx.spi.ConnectionException;
import com.hashnot.fx.spi.IMarket;
import com.hashnot.fx.spi.IMarketSession;
import com.hashnot.fx.spi.Offer;
import com.hashnot.fx.util.StoringCookieManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * @author Rafał Krupiński
 */
public class CFMarketSession implements IMarketSession {
    private static final Logger log = LoggerFactory.getLogger(CFMarketSession.class);
    private static final String URL_LOGIN = "https://app.currencyfair.com/login/login";
    private static final String URL_MARKET = "https://app.currencyfair.com/exchange/market";
    private static final String KEY_SECURITY = "security";
    private WebClient webClient;
    private StoringCookieManager cookieManager;
    private String securityTag;
    private MarketplaceItemConverter marketplaceItemConverter = new MarketplaceItemConverter();
    private CFMarket parent;
    private Gson gson = new Gson();

    public CFMarketSession(WebClient wc, StoringCookieManager cm, CFMarket parent) {
        webClient = wc;
        cookieManager = cm;
        this.parent = parent;
    }

    @Override
    public Set<String> listCurrencies() {
        return null;
    }

    @Override
    public List<Offer> getOffers(String base, String counter) {
        if (securityTag == null)
            throw new IllegalStateException("no security tag");
        try {
            List<Offer> offers = doGetOffers(base, counter);
            offers.addAll(doGetOffers(counter, base));
            return offers;
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    private List<Offer> doGetOffers(String base, String counter) throws IOException {
        WebRequest marketRequest = new WebRequest(new URL("https://app.currencyfair.com/marketplace/getmarket"), HttpMethod.POST);
        List<NameValuePair> params = new LinkedList<>();

        //this is reversed, because it's what I have to offer, not other users' offers
        params.add(new NameValuePair("currencyFrom", counter));
        params.add(new NameValuePair("currencyTo", base));

        params.add(new NameValuePair(KEY_SECURITY, securityTag));
        marketRequest.setRequestParameters(params);
        marketRequest.setAdditionalHeader("Referrer", URL_MARKET);
        Page result = webClient.getPage(marketRequest);

        MarketContainer market = gson.fromJson(new InputStreamReader(result.getWebResponse().getContentAsStream()), MarketContainer.class);
        return marketplaceItemConverter.toOffers(market, parent);
    }

    @Override
    public Map<String, BigDecimal> getCurrentWallet() {
        return null;
    }

    @Override
    public void close() {
        try {
            cookieManager.close();
        } catch (IOException e) {
            throw new ConnectionException(e);
        }
    }

    @Override
    public IMarket getMarket() {
        return parent;
    }

    public void login() throws IOException {
        HtmlPage init = webClient.getPage(URL_MARKET);
        if (uriEquals(init, URL_LOGIN)) {
            doLogin(webClient);
            init = webClient.getPage(URL_MARKET);
        }
        securityTag = ((HtmlInput) init.getElementById("getMarketCsrf")).getValueAttribute();

    }

    private HtmlPage doLogin(WebClient webClient) throws IOException {
        HtmlPage loginPage = webClient.getPage(URL_LOGIN);
        HtmlForm loginForm = loginPage.getFormByName("login-form");
        loginForm.getInputByName("email").setValueAttribute("");
        loginForm.getInputByName("password").setValueAttribute("");
        securityTag = loginForm.getInputByName(KEY_SECURITY).getValueAttribute();
        HtmlButton submit = loginForm.getButtonByName("");
        HtmlPage next = submit.click();
        if (uriEquals(next, "https://app.currencyfair.com/auth/challenge/twofactor")) {
            next = twoFactor(next);
        }

        log.debug(next.getUrl().toExternalForm());
        return next;
    }

    private static boolean uriEquals(Page page, String uri) {
        URI pageUri;
        URL pageUrl = page.getUrl();
        try {
            pageUri = pageUrl.toURI();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(pageUrl.toExternalForm(), e);
        }
        try {
            return pageUri.equals(new URI(uri));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(uri, e);
        }
    }

    private HtmlPage twoFactor(HtmlPage twoFactor) throws IOException {
        HtmlAnchor sendMsgButton = (HtmlAnchor) twoFactor.getElementById("panel1-sms-send");
        HtmlPage afterClick = sendMsgButton.click();
        log.info("twoFactor: {}", twoFactor);
        log.info("after click: {}", afterClick);

        HtmlForm verifyForm = (HtmlForm) afterClick.getElementById("verifytoken");
        PasswordFuture password = new PasswordFuture();
        new PasswordDialog(password).setVisible(true);


        String pass;
        try {
            log.info("waiting for user input...");
            pass = password.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ConnectionException(e);
        }
        verifyForm.getInputByName("token").setValueAttribute(pass);

        return ((HtmlAnchor) twoFactor.getElementById("panel2-verify")).click();
    }

}
