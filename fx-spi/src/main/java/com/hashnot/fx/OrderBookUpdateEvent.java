package com.hashnot.fx;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.hashnot.fx.util.Numbers;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Rafał Krupiński
 */
public class OrderBookUpdateEvent {

    private Supplier<OrderBook> changes = Suppliers.memoize(this::diff);

    public final CurrencyPair pair;

    public final OrderBook before;
    public final OrderBook after;

    public OrderBookUpdateEvent(CurrencyPair pair, OrderBook before, OrderBook after) {
        this.pair = pair;
        this.before = before;
        this.after = after;
    }

    public OrderBook getChanges() {
        return changes.get();
    }

    protected OrderBook diff() {
        List<LimitOrder> asks = diff(before, after, Order.OrderType.ASK);
        List<LimitOrder> bids = diff(before, after, Order.OrderType.BID);
        return new OrderBook(after.getTimeStamp(), asks, bids);
    }

    private List<LimitOrder> diff(OrderBook beforeBook, OrderBook afterBook, Order.OrderType type) {
        List<LimitOrder> result = new LinkedList<>();

        List<LimitOrder> beforeOrders = beforeBook.getOrders(type);
        List<LimitOrder> afterOrders = afterBook.getOrders(type);

        for (int i = 0, j = 0; j < afterOrders.size() && i < beforeOrders.size(); ) {
            LimitOrder before = beforeOrders.get(i);
            LimitOrder after = afterOrders.get(j);

            BigDecimal priceDiff = after.getLimitPrice().subtract(before.getLimitPrice());
            if (Numbers.equals(priceDiff, BigDecimal.ZERO)) {
                BigDecimal amountDiff = after.getTradableAmount().subtract(before.getTradableAmount());
                if(!Numbers.equals(amountDiff, BigDecimal.ZERO))
                    result.add(new LimitOrder(type,amountDiff,before.getCurrencyPair(),null,null,before.getLimitPrice()));
                ++i;
                ++j;
                continue;
            }
            // TODO add smaller order to result, increase index

        }

        return result;
    }
}
