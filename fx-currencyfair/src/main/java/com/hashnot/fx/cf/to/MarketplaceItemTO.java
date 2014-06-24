package com.hashnot.fx.cf.to;

import java.math.BigDecimal;

/**
 * CurrencyFair Marketplace item transport object got from getmarket POST
 * {
 * "type": "AVAIL",
 * "rate": 4.1025,
 * "rateTransport": "4.1025",
 * "amount": "11,395.58 EUR",
 * "increment": 2.0e-5,
 * "incrementTransport": "0.00002",
 * "currencyFrom": "EUR",
 * "currencyTo": "PLN",
 * "rateInverse": null,
 * "rateInverseTransport": null
 * },
 *
 * @author Rafał Krupiński
 */
public class MarketplaceItemTO {
    public enum Availability {
        AVAIL, WAIT
    }

    public Availability type;
    public BigDecimal rate;
    public String rateTransport;
    public String amount;
    public BigDecimal increment;
    public String incrementTransport;
    public String currencyFrom;
    public String currencyTo;
    public BigDecimal rateInverse;
    public String rateInverseTransport;
}
