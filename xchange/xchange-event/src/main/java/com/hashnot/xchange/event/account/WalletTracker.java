package com.hashnot.xchange.event.account;

import com.hashnot.xchange.event.trade.IOrderTracker;
import com.hashnot.xchange.event.trade.IUserTradeListener;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.hashnot.xchange.ext.trade.IOrderPlacementListener;
import com.hashnot.xchange.ext.trade.OrderCancelEvent;
import com.hashnot.xchange.ext.trade.OrderPlacementEvent;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.MarketOrder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author Rafał Krupiński
 */
public class WalletTracker implements IWalletMonitor, IOrderPlacementListener, IUserTradeListener {
    //final private static Logger log = LoggerFactory.getLogger(WalletTracker.class);

    final private IOrderTracker orderTracker;

    /**
     * Used only to keep and update wallets state, don't register any listeners.
     */
    final private IWalletMonitor walletMonitor;

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
    public void orderCanceled(OrderCancelEvent evt) {
        disableWalletMonitor();
    }

    public Future<Map<String, BigDecimal>> update() {
        return walletMonitor.update();
    }

    @Override
    public void marketOrderPlaced(OrderPlacementEvent<MarketOrder> evt) {
    }

    @Override
    public void limitOrderPlaced(OrderPlacementEvent<LimitOrder> evt) {
        enableWalletMonitor();
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
