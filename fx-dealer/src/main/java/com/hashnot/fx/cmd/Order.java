package com.hashnot.fx.cmd;

import com.hashnot.fx.framework.IUserTradeListener;
import com.hashnot.fx.framework.Main;
import com.hashnot.fx.framework.UserTradeEvent;
import com.hashnot.fx.framework.impl.TrackingUserTradesMonitor;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.event.IOrderBookListener;
import com.hashnot.xchange.event.OrderBookUpdateEvent;
import com.hashnot.xchange.ext.util.Orders;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.service.polling.PollingTradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xeiam.xchange.dto.Order.OrderType;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static com.xeiam.xchange.dto.trade.LimitOrder.Builder.from;

/**
 * @author Rafał Krupiński
 */
public class Order {
    final private static Logger log = LoggerFactory.getLogger(Order.class);

    /**
     * args:
     * setup.groovy
     * exchange name
     * BID/ASK
     * amount or "1/${value}"
     * pair
     * price or delta or auto (default)
     */
    public static void main(String[] args) throws Exception {
        Collection<IExchangeMonitor> exchanges = Main.load(args[0], Executors.newScheduledThreadPool(10));
        IExchangeMonitor x = find(exchanges, args[1]);
        x.start();
        order(x, args[2], args[3], args[4], args.length == 6 ? args[5] : null);
    }

    private static void order(IExchangeMonitor x, String type, String amount, String pair, String price) throws Exception {
        OrderType orderType = Character.toLowerCase(type.charAt(0)) == 'a' ? ASK : BID;

        BigDecimal tradableAmount = null;
        BigDecimal value = null;
        if (amount.startsWith("1/"))
            value = new BigDecimal(amount.substring(2));
        else
            tradableAmount = new BigDecimal(amount);

        int slash = pair.indexOf('/');
        CurrencyPair currencyPair = new CurrencyPair(pair.substring(0, slash), pair.substring(slash + 1));

        if (price != null)
            throw new IllegalArgumentException("price currently unsupported");
        BigDecimal order = order(x, orderType, tradableAmount, value, currencyPair);
        log.info("{}", order);
    }

    private static BigDecimal order(IExchangeMonitor x, OrderType type, BigDecimal amount, BigDecimal value, CurrencyPair pair) throws ExecutionException, InterruptedException {

        Dealer dealer = new Dealer(x, new LimitOrder(type, amount, pair, null, null, null), value);

        x.getOrderBookMonitor().addOrderBookListener(dealer, pair);
        new TrackingUserTradesMonitor(x.getUserTradesMonitor()).addTradeListener(dealer);

        return dealer.get();
    }

    private static IExchangeMonitor find(Collection<IExchangeMonitor> exchanges, String name) {
        for (IExchangeMonitor x : exchanges) {
            log.debug("{}", x);
            if (x.toString().matches(name))
                return x;
        }
        throw new IllegalArgumentException(name);
    }

}

class Dealer implements IOrderBookListener, IUserTradeListener {
    final private static Logger log = LoggerFactory.getLogger(Dealer.class);
    private IExchangeMonitor x;
    private LimitOrder order;
    private String orderId;
    private BigDecimal value = BigDecimal.ZERO;
    private BigDecimal amount = BigDecimal.ZERO;

    private volatile boolean done = false;

    //false if partially executed
    private AtomicBoolean edit = new AtomicBoolean(true);

    Dealer(IExchangeMonitor x, LimitOrder order, BigDecimal tradableValue) {
        this.x = x;
        this.order = order;
    }

    @Override
    public void orderBookChanged(OrderBookUpdateEvent event) {
        List<LimitOrder> orders = event.orderBook.getOrders(order.getType());
        log.info("changed {}", orders);
        if (orders.isEmpty())
            throw new IllegalStateException("No " + order.getType() + " orders found");

        if (edit.get()) {
            BigDecimal price = Orders.betterPrice(orders.get(0).getLimitPrice(), x.getMarketMetadata(order.getCurrencyPair()).getPriceStep(), order.getType());

            try {
                PollingTradeService tradeService = x.getExchange().getPollingTradeService();
                LimitOrder order = from(this.order).limitPrice(price).build();
                if (false) {
                    if (orderId != null)
                        tradeService.cancelOrder(orderId);
                    orderId = tradeService.placeLimitOrder(order);
                }
                log.info("trade {}", order);
            } catch (IOException e) {
                log.warn("Error", e);
            }
        }
    }

    @Override
    public synchronized void trade(UserTradeEvent evt) {
        if (evt.current == null) {
            x.getOrderBookMonitor().removeOrderBookListener(this, evt.monitored.getCurrencyPair());
            done = true;
            notify();
        } else
            edit.set(false);
    }

    public BigDecimal get() throws InterruptedException, ExecutionException {
        while (!done) {
            synchronized (this) {
                wait();
            }
        }
        return value;
    }

}
