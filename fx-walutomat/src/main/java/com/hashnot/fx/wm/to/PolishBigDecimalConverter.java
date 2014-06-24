package com.hashnot.fx.wm.to;

import org.simpleframework.xml.convert.Converter;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
* @author Rafał Krupiński
*/
public class PolishBigDecimalConverter implements Converter<BigDecimal> {
    DecimalFormat formatter;

    {
        formatter = new DecimalFormat("#,##0.0#", new DecimalFormatSymbols(new Locale("PL","pl")));
        formatter.setParseBigDecimal(true);

    }

    @Override
    public BigDecimal read(InputNode node) throws Exception {
        return (BigDecimal) formatter.parse(node.getValue());
    }

    @Override
    public void write(OutputNode node, BigDecimal value) throws Exception {
        node.setValue(formatter.format(value));
    }
}
