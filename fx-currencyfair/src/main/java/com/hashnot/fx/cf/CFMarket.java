package com.hashnot.fx.cf;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.hashnot.fx.spi.ConnectionException;
import com.hashnot.fx.spi.IMarket;
import com.hashnot.fx.util.ConfiguringCookieManager;
import com.hashnot.fx.util.StoringCookieManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.Principal;

/**
 * @author Rafał Krupiński
 */
public class CFMarket implements IMarket {
    private static final Logger log = LoggerFactory.getLogger(CFMarket.class);

    @Override
    public CFMarketSession open(Principal principal) throws ConnectionException {
        WebClient wc = new WebClient(BrowserVersion.FIREFOX_24);
        wc.getOptions().setCssEnabled(false);
        wc.getOptions().setThrowExceptionOnScriptError(false);

        StoringCookieManager cm = null;
        try {
            cm = new StoringCookieManager(new File("fxcookies.json"));
            wc.setCookieManager(new ConfiguringCookieManager(cm));
            CFMarketSession session = new CFMarketSession(wc, cm, this);
            session.login();
            return session;
        } catch (IOException e) {
            throw new ConnectionException(e);
        } finally {
            if (cm != null) {
                try {
                    cm.close();
                } catch (IOException e) {
                    log.warn("Error", e);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "currencyfair.com";
    }
}
