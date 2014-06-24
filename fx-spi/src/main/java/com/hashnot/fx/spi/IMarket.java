package com.hashnot.fx.spi;

import java.io.IOException;
import java.security.Principal;

/**
 * @author Rafał Krupiński
 */
public interface IMarket {
    IMarketSession open(Principal principal) throws ConnectionException;
}
