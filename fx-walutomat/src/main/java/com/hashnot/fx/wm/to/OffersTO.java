package com.hashnot.fx.wm.to;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.List;

/**
 * @author Rafał Krupiński
 */
@Root
public class OffersTO {
    @ElementList(entry = "offer", inline = true)
    public List<OfferTO> offers;
}
