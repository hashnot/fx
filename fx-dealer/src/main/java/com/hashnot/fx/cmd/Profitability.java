package com.hashnot.fx.cmd;

import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.hashnot.fx.framework.IStrategy;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.IExchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.marketdata.OrderBook;
import com.xeiam.xchange.dto.trade.LimitOrder;

import javax.inject.Inject;
import javax.inject.Named;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static com.hashnot.xchange.ext.util.Numbers.BigDecimal.TWO;
import static com.hashnot.xchange.ext.util.Numbers.lt;
import static java.lang.System.out;
import static java.math.BigDecimal.*;

/**
 * @author Rafał Krupiński
 */
public class Profitability implements IStrategy {

    static BigDecimal[] amounts = new BigDecimal[]{
            ONE.movePointLeft(2),
            ONE.movePointLeft(1),
            new BigDecimal(".2"),
            new BigDecimal(".5"),
            ONE,
            TWO,
            new BigDecimal(5),
            ONE.movePointRight(1),
            ONE.movePointRight(2)
    };

    @Inject
    @Named("exitHook")
    private Runnable exitHook;

    @Override
    public void init(Collection<IExchangeMonitor> monitors, Collection<CurrencyPair> pairs) throws InterruptedException {
        Map<IExchangeMonitor, OrderBook> books = new ConcurrentHashMap<>();

        CountDownLatch count = new CountDownLatch(monitors.size());

        for (IExchangeMonitor monitor : monitors) {
            monitor.start();
            monitor.getAsyncExchange().getMarketDataService().getOrderBook(CurrencyPair.BTC_EUR, (future) -> {
                try {
                    OrderBook orderBook = future.get();
                    books.put(monitor, orderBook);
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                } finally {
                    count.countDown();
                    monitor.stop();
                }
            });
        }

        count.await();

        // (amount, (exchange,orderType), value)
        Table<BigDecimal, MarketSide, BigDecimal> values = Tables.newCustomTable(new TreeMap<>(), HashMap::new);

        for (Map.Entry<IExchangeMonitor, OrderBook> e : books.entrySet()) {
            getValues(values, Order.OrderType.ASK, e.getValue(), e.getKey().getExchange());
            getValues(values, Order.OrderType.BID, e.getValue(), e.getKey().getExchange());
        }

        boolean head = false;
        for (Map.Entry<BigDecimal, Map<MarketSide, BigDecimal>> e : values.rowMap().entrySet()) {
            if (!head) {
                out.print(";");
                for (MarketSide marketSide : e.getValue().keySet()) {
                    out.print(marketSide.market.exchange + "/" + marketSide.side + ";");
                }
                head = true;
                out.println();
            }
            out.print(e.getKey() + ";");
            for (Map.Entry<MarketSide, BigDecimal> f : e.getValue().entrySet()) {
                out.print(f.getValue().divide(e.getKey(), 5, ROUND_HALF_EVEN) + ";");
            }
            out.println();
        }
        exitHook.run();
    }

    protected static void getValues(Table<BigDecimal, MarketSide, BigDecimal> values, Order.OrderType type, OrderBook orderBook, IExchange exchange) {
        List<BigDecimal> myValues = compute(orderBook, type);
        update(values, myValues, new MarketSide(exchange, CurrencyPair.BTC_EUR, type));
    }

    private static void update(Table<BigDecimal, MarketSide, BigDecimal> table, List<BigDecimal> values, MarketSide marketSide) {
        for (int i = 0; i < amounts.length; i++) {
            BigDecimal amount = amounts[i];
            table.put(amount, marketSide, values.get(i));
        }
    }

    /**
     * @return list of cost of buying amount[k] of bitcoins
     */
    private static List<BigDecimal> compute(OrderBook orderBook, Order.OrderType type) {
        List<LimitOrder> orders = orderBook.getOrders(type);
        List<BigDecimal> costs = new ArrayList<>(amounts.length);

        BigDecimal totalAmount = ZERO;
        BigDecimal totalValue = ZERO;

        int i = 0;
        for (LimitOrder order : orders) {
            BigDecimal amount = order.getTradableAmount();
            BigDecimal newTotalAmount = totalAmount.add(amount);
            BigDecimal price = order.getLimitPrice();


            for (; i < amounts.length; i++) {
                BigDecimal amountLimit = amounts[i];

                if (!lt(newTotalAmount, amountLimit)) {
                    BigDecimal amountDiff = amountLimit.subtract(totalAmount);
                    BigDecimal blockValue = amountDiff.multiply(price);
                    BigDecimal amountValue = totalValue.add(blockValue);
                    costs.add(amountValue);
                } else
                    break;
            }


            if (i == amounts.length) break;

            totalAmount = newTotalAmount;
            totalValue = totalValue.add(amount.multiply(price));
        }
        return costs;
    }
}
