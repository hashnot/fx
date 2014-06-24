package com.hashnot.fx.wm;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.hashnot.fx.spi.ConnectionException;
import com.hashnot.fx.spi.IMarket;
import com.hashnot.fx.spi.IMarketSession;

import java.security.Principal;

/**
 * @author Rafał Krupiński
 */
public class WMMarket implements IMarket {
    @Override
    public IMarketSession open(Principal principal) throws ConnectionException {
        return new WMMarketSession(this, new WebClient(BrowserVersion.FIREFOX_24));
    }

    @Override
    public String toString() {
        return "walutomat.pl";
    }
}
