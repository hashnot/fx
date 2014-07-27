package com.hashnot.fx;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author Rafał Krupiński
 */
public class BigDecimalTest {
    @Test
    public void testRounding() {
        BigDecimal v = new BigDecimal(".009999").setScale(2, RoundingMode.FLOOR);
        System.out.println(v);
    }
}
