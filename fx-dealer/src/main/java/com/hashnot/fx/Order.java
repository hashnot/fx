package com.hashnot.fx;

import com.hashnot.fx.framework.Main;
import com.hashnot.fx.spi.IOrderBookListener;
import com.hashnot.fx.spi.ITradeListener;
import com.hashnot.fx.spi.ext.IExchange;
import com.hashnot.fx.util.Orders;
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
        Collection<IExchange> exchanges = Main.load(args[0]);
        IExchange x = find(exchanges, args[1]);
        x.start(Executors.newScheduledThreadPool(10));
        order(x, args[2], args[3], args[4], args.length == 6 ? args[5] : null);
    }

    private static void order(IExchange x, String type, String amount, String pair, String price) throws Exception {
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

    private static BigDecimal order(IExchange x, OrderType type, BigDecimal amount, BigDecimal value, CurrencyPair pair) throws ExecutionException, InterruptedException {

        Dealer dealer = new Dealer(x, new LimitOrder(type, amount, pair, null, null, null), value);

        x.addOrderBookListener(pair, type, x.getMinimumTrade(pair.baseSymbol), null, dealer);
        x.addTradeListener(pair, dealer);

        return dealer.get();
    }

    private static IExchange find(Collection<IExchange> exchanges, String name) {
        for (IExchange x : exchanges) {
            log.debug("{}", x);
            if (x.toString().matches(name))
                return x;
        }
        throw new IllegalArgumentException(name);
    }

}

class Dealer implements IOrderBookListener, ITradeListener {
    final private static Logger log = LoggerFactory.getLogger(Dealer.class);
    private IExchange x;
    private LimitOrder order;
    private String orderId;
    private BigDecimal value = BigDecimal.ZERO;
    private BigDecimal amount = BigDecimal.ZERO;

    private volatile boolean done = false;

    //false if partially executed
    private AtomicBoolean edit = new AtomicBoolean(true);

    Dealer(IExchange x, LimitOrder order, BigDecimal tradableValue) {
        this.x = x;
        this.order = order;
    }

    @Override
    public void changed(List<LimitOrder> orders) {
        log.info("changed {}", orders);
        if (orders.isEmpty())
            throw new IllegalStateException("No " + order.getType() + " orders found");

        if (edit.get()) {
            BigDecimal price = Orders.betterPrice(orders.get(0).getLimitPrice(), x.getLimitPriceUnit(order.getCurrencyPair()), order.getType());

            try {
                PollingTradeService tradeService = x.getPollingTradeService();
                LimitOrder order = Orders.withPrice(this.order, price);
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
    public synchronized void trade(LimitOrder monitored, LimitOrder current) {
        if (current == null) {
            x.removeOrderBookListener(this);
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
