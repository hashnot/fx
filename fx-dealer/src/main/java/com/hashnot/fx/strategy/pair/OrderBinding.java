package com.hashnot.fx.strategy.pair;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.util.List;

/**
 * @author Rafał Krupiński
 */
public class OrderBinding {
    public final Exchange openExchange;
    public final Exchange closeExchange;
    public final LimitOrder openedOrder;

    public String openOrderId;
    public List<LimitOrder> closingOrders;

    // Unneded, because we're closing orders according to incoming trades and we also know when open order is closed
    // public BigDecimal filled = BigDecimal.ZERO;

    public OrderBinding(Exchange openExchange, Exchange closeExchange, LimitOrder openedOrder, List<LimitOrder> closingOrders) {
        this.openExchange = openExchange;
        this.closeExchange = closeExchange;
        this.openedOrder = openedOrder;
        this.closingOrders = closingOrders;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderBinding)) return false;

        OrderBinding that = (OrderBinding) o;

        if (!closeExchange.equals(that.closeExchange)) return false;
        if (!closingOrders.equals(that.closingOrders)) return false;
        if (!openExchange.equals(that.openExchange)) return false;
        if (openOrderId != null ? !openOrderId.equals(that.openOrderId) : that.openOrderId != null) return false;
        if (!openedOrder.equals(that.openedOrder)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = openExchange.hashCode();
        result = 31 * result + closeExchange.hashCode();
        result = 31 * result + openedOrder.hashCode();
        result = 31 * result + (openOrderId != null ? openOrderId.hashCode() : 0);
        result = 31 * result + closingOrders.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "OrderUpdateEvent{" +
                "openExchange=" + openExchange +
                ", closeExchange=" + closeExchange +
                ", openedOrder=" + openedOrder +
                ", openOrderId='" + openOrderId + '\'' +
                ", closingOrders=" + closingOrders +
                '}';
    }
}
