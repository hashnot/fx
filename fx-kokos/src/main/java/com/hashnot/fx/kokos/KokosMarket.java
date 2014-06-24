package com.hashnot.fx.kokos;

import com.hashnot.fx.spi.ConnectionException;
import com.hashnot.fx.spi.IMarket;
import com.hashnot.fx.spi.IMarketSession;

import java.security.Principal;

/**
 * @author Rafał Krupiński
 */
public class KokosMarket implements IMarket {
    @Override
    public IMarketSession open(Principal principal) throws ConnectionException {
        return new KokosMarketSession(this);
    }

    @Override
    public String toString() {
        return "waluty.kokos.pl";
    }
}
