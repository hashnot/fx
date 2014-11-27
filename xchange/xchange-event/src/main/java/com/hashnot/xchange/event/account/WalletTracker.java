package com.hashnot.xchange.event.account;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.hashnot.xchange.event.trade.IOrderTracker;
import com.hashnot.xchange.event.trade.IUserTradeListener;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.hashnot.xchange.ext.trade.IOrderPlacementListener;
import com.hashnot.xchange.ext.trade.OrderCancelEvent;
import com.hashnot.xchange.ext.trade.OrderEvent;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hashnot.xchange.ext.util.Multiplexer.multiplex;

/**
 * @author Rafał Krupiński
 */
public class WalletTracker implements IWalletMonitor, IOrderPlacementListener, IUserTradeListener, IWalletListener {
    //final private static Logger log = LoggerFactory.getLogger(WalletTracker.class);

    final private IOrderTracker orderTracker;

    /**
     * Used only to keep and update wallets state, don't register any listeners.
     */
    final private IWalletMonitor walletMonitor;

    final private Multimap<String, IWalletListener> listeners = Multimaps.newSetMultimap(new HashMap<>(), () -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

    final private Iterable<IWalletListener> adHocListeners = Arrays.asList(this);

    public WalletTracker(IOrderTracker orderTracker, IWalletMonitor walletMonitor) {
        this.orderTracker = orderTracker;
        this.walletMonitor = walletMonitor;
    }

    private void enableWalletMonitor() {
        orderTracker.addTradeListener(this);
    }

    private void disableWalletMonitor() {
        if (orderTracker.getMonitored().isEmpty())
            orderTracker.removeTradeListener(this);
    }

    @Override
    public void walletUpdate(WalletUpdateEvent evt) {
        multiplex(listeners.get(evt.currency), evt, IWalletListener::walletUpdate);
    }

    @Override
    public void orderCanceled(OrderCancelEvent evt) {
        disableWalletMonitor();
    }

    @Override
    public void update(Iterable<IWalletListener> listeners) {
        walletMonitor.update(listeners);
    }

    private void update() {
        update(adHocListeners);
    }

    @Override
    public void marketOrderPlaced(OrderEvent<MarketOrder> evt) {
        update();
    }

    @Override
    public void limitOrderPlaced(OrderEvent<LimitOrder> evt) {
        enableWalletMonitor();
    }

    @Override
    public void addWalletListener(IWalletListener listener, String currency) {
        listeners.put(currency, listener);
    }

    @Override
    public void removeWalletListener(IWalletListener listener, String currency) {
        listeners.remove(currency, listener);
    }

    @Override
    public void trade(UserTradeEvent evt) {
        update();

        if (evt.current == null)
            if (orderTracker.getMonitored().isEmpty())
                disableWalletMonitor();
    }

    @Override
    public BigDecimal getWallet(String currency) {
        return walletMonitor.getWallet(currency);
    }

    @Override
    public Map<String, BigDecimal> getWallet() {
        return walletMonitor.getWallet();
    }
}
