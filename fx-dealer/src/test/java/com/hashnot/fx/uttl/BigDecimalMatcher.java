package com.hashnot.fx.uttl;

import com.hashnot.xchange.ext.util.Comparables;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import java.math.BigDecimal;

/**
* @author Rafał Krupiński
*/
public class BigDecimalMatcher extends BaseMatcher<BigDecimal> {
    final BigDecimal value;

    public BigDecimalMatcher(BigDecimal value) {
        this.value = value;
    }

    @Override
    public boolean matches(Object item) {
        return item instanceof BigDecimal && Comparables.eq(value, (BigDecimal) item);
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(value);
    }
}
