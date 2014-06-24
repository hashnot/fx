package com.hashnot.fx.wm.to;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.convert.Convert;

import java.math.BigDecimal;

/**
 * @author Rafał Krupiński
 */
@Root(strict = false)
public class OfferTO {
    /**
     * This misspell is required to match incoming document
     */
    @Element
    @Convert(PolishBigDecimalConverter.class)
    public BigDecimal ammount;
    @Element
    public BigDecimal rate;
    @Element
    public String from;
    @Element
    public String to;

}
