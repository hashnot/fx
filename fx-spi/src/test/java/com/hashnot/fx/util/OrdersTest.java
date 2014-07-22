package com.hashnot.fx.util;

import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.trade.LimitOrder;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

public class OrdersTest {

    @Test
    public void testIncomingAmount() throws Exception {
        BigDecimal onee_10 = new BigDecimal("0.0043867661");
        LimitOrder order = new LimitOrder(Order.OrderType.ASK, onee_10, CurrencyPair.BTC_EUR,null,null, onee_10);
        order.setNetPrice(new BigDecimal("2"));
        System.out.println(Orders.incomingAmount(order));
    }
}