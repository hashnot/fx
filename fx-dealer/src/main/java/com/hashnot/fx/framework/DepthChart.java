/**
 * Copyright (C) 2012 - 2014 Xeiam LLC http://xeiam.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hashnot.fx.framework;

import com.hashnot.fx.util.CurrencyPairUtil;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;
import info.monitorenter.gui.chart.Chart2D;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.traces.Trace2DLtdSorted;
import info.monitorenter.gui.chart.traces.painters.TracePainterFill;

import javax.swing.*;
import java.math.BigDecimal;

/**
 * Demonstrate requesting OrderBook from Bitstamp and plotting it using XChart.
 */
public class DepthChart {

    private BigDecimal valueLimit = new BigDecimal("10000");

    public void createChart(CurrencyPair pair, OrderBook... orderBooks) {
        // Create Chart
        Chart2D chart = new Chart2D();
        ITrace2D trace = new Trace2DLtdSorted(2000);
        trace.setTracePainter(new TracePainterFill(chart));
        chart.addTrace(trace);

        // Customize Chart

        for (OrderBook orderBook : orderBooks)
            plotOrderBook(orderBook, trace);

        JFrame f = new JFrame();
        f.getContentPane().add(chart);
        f.setSize(800, 600);
        f.setVisible(true);
    }

    private void plotOrderBook(OrderBook orderBook, ITrace2D chart) {
        addOrders(orderBook, chart, Order.OrderType.BID, true);
        addOrders(orderBook, chart, Order.OrderType.ASK, false);
    }

    private void addOrders(OrderBook orderBook, ITrace2D chart, Order.OrderType type, boolean reverse) {
/*
        List<Number> xData = new ArrayList<>();
        List<Number> yData = new ArrayList<>();
*/
        BigDecimal accumulatedUnits = BigDecimal.ZERO;
        BigDecimal accumulatedValue = BigDecimal.ZERO;

        for (LimitOrder limitOrder : CurrencyPairUtil.get(orderBook, type)) {
            //xData.open(limitOrder.getLimitPrice());
            accumulatedUnits = accumulatedUnits.add(limitOrder.getTradableAmount());
            accumulatedValue = accumulatedValue.add(limitOrder.getTradableAmount().multiply(limitOrder.getLimitPrice()));
            //yData.open(accumulatedUnits);

            if (accumulatedValue.compareTo(valueLimit) >= 0) break;

            chart.addPoint(limitOrder.getLimitPrice().doubleValue(), accumulatedUnits.doubleValue());
        }


        //Series series = chart.addSeries(type.name(), xData, yData);
        //series.setMarker(SeriesMarker.NONE);

/*
        if (reverse) {
            Collections.reverse(xData);
            Collections.reverse(yData);
        }
*/
    }
}
