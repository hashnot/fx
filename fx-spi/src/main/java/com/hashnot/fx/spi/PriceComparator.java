package com.hashnot.fx.spi;

import java.util.Comparator;

/**
 * @author Rafał Krupiński
 */
public class PriceComparator implements Comparator<Offer> {
    public static final PriceComparator COMPARATOR = new PriceComparator();

    @Override
    public int compare(Offer o1, Offer o2) {
        return o1.price.compareTo(o2.price);
    }

    public static Offer min(Offer o1, Offer o2) {
        return COMPARATOR.compare(o1, o2) <= 0 ? o1 : o2;
    }

    public static Offer max(Offer o1, Offer o2) {
        return COMPARATOR.compare(o1, o2) >= 0 ? o1 : o2;
    }
}
