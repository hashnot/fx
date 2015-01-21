package com.hashnot.fx.cmd;

import com.hashnot.fx.framework.IStrategy;
import com.hashnot.fx.framework.Main;
import com.hashnot.xchange.async.IAsyncExchange;
import com.hashnot.xchange.async.impl.AsyncSupport;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.Market;
import com.hashnot.xchange.ext.util.Orders;
import com.hashnot.xchange.ext.util.Tickers;
import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.currency.CurrencyPair;
import com.xeiam.xchange.dto.Order;
import com.xeiam.xchange.dto.account.AccountInfo;
import com.xeiam.xchange.dto.marketdata.Ticker;
import com.xeiam.xchange.dto.marketdata.TradeServiceHelper;
import com.xeiam.xchange.dto.trade.LimitOrder;
import com.xeiam.xchange.dto.trade.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static com.hashnot.xchange.ext.util.Comparables.*;
import static com.hashnot.xchange.ext.util.Orders.revert;
import static com.xeiam.xchange.dto.Order.OrderType.ASK;
import static com.xeiam.xchange.dto.Order.OrderType.BID;
import static java.math.BigDecimal.valueOf;

/**
 * @author Rafał Krupiński
 */
public class Balancer implements IStrategy {
    final private static Logger log = LoggerFactory.getLogger(Balancer.class);
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    @Inject
    @Named(Main.EXIT_HOOK)
    Runnable exitHook;

    private Map<Market, Ticker> tickers;
    private Map<Exchange, AccountInfo> infos;
    private Map<Market, TradeServiceHelper> marketMetadata;

    @Override
    public void init(Collection<IExchangeMonitor> exchangeMonitors, Collection<CurrencyPair> pairs) throws Exception {
        // obsługuję tylko balans między btc a eur
        assert pairs.size() == 1;

        tickers = new ConcurrentHashMap<>(exchangeMonitors.size() * pairs.size());
        infos = new ConcurrentHashMap<>(exchangeMonitors.size());
        marketMetadata = new ConcurrentHashMap<>(exchangeMonitors.size() * pairs.size());
        CountDownLatch count = new CountDownLatch(exchangeMonitors.size() * pairs.size() + exchangeMonitors.size() * 2);

        for (IExchangeMonitor exchangeMonitor : exchangeMonitors) {
            IAsyncExchange x = exchangeMonitor.getAsyncExchange();
            log.debug("getAccountInfo from {}", x);
            x.getAccountService().getAccountInfo(i -> {
                try {
                    AccountInfo info = AsyncSupport.get(i);
                    infos.put(exchangeMonitor.getExchange(), info);
                } catch (IOException e) {
                    log.warn("Error from {}", exchangeMonitor, e);
                } finally {
                    count.countDown();
                }
            });
            for (CurrencyPair pair : pairs) {
                x.getMarketDataService().getTicker(pair, future -> {
                    try {
                        Ticker ticker = AsyncSupport.get(future);
                        tickers.put(new Market(exchangeMonitor.getExchange(), pair), ticker);
                    } catch (IOException e) {
                        log.warn("Error from {}", exchangeMonitor, e);
                    } finally {
                        count.countDown();
                    }
                });
            }
            x.getTradeService().getMetadata((a) -> {
                try {
                    Map<CurrencyPair, ? extends TradeServiceHelper> xmeta = AsyncSupport.get(a);
                    for (Map.Entry<CurrencyPair, ? extends TradeServiceHelper> e : xmeta.entrySet()) {
                        marketMetadata.put(new Market(exchangeMonitor.getExchange(), e.getKey()), e.getValue());
                    }
                } catch (IOException e) {
                    log.warn("Error from {}", exchangeMonitor, e);
                } finally {
                    count.countDown();
                }
            });
        }

        log.debug("await");
        count.await();
        log.debug("ok.");

        Map<Exchange, Map<String, BigDecimal>> allWallets = new HashMap<>(exchangeMonitors.size());

        for (Map.Entry<Exchange, AccountInfo> e : infos.entrySet()) {
            List<Wallet> wallets = e.getValue().getWallets();
            Map<String, BigDecimal> walletMap = new HashMap<>();
            allWallets.put(e.getKey(), walletMap);
            for (Wallet wallet : wallets) {
                BigDecimal balance = wallet.getBalance();
                log.debug("{}", wallet);
                walletMap.put(wallet.getCurrency(), balance);
            }
        }

        for (Map.Entry<Exchange, Map<String, BigDecimal>> e : allWallets.entrySet()) {
            Exchange x = e.getKey();
            for (CurrencyPair pair : pairs) {
                log.info("{}", x);
                handleMarket(new Market(x, pair), allWallets.get(x));
            }
        }
        exitHook.run();
    }

    private void handleMarket(Market market, Map<String, BigDecimal> wallets) {
        CurrencyPair pair = market.listing;
        Ticker ticker = tickers.get(market);
        if (ticker == null) return;
        log.info("{}\tASK {}\tBID {}", ticker.getCurrencyPair(), ticker.getAsk(), ticker.getBid());
        TradeServiceHelper meta = marketMetadata.get(market);
        log.info("{}", meta);
        BigDecimal feeFactor = infos.get(market.exchange).getTradingFee();
        describeWallet(wallets, pair, ticker.getAsk(), feeFactor);

        String base = pair.baseSymbol;
        BigDecimal baseWallet = wallets.get(base);
        BigDecimal counterWallet = wallets.get(pair.counterSymbol);
        BigDecimal netPrice = Orders.getNetPrice(ticker.getAsk(), BID, feeFactor);
        BigDecimal netCounterWalletInBase = counterWallet.divide(netPrice, counterWallet.scale(), ROUNDING);

        BigDecimal minTrade = meta.getAmountMinimum();
        BigDecimal lowThreshold = valueOf(2).multiply(minTrade);
        // if both wallets are at least 2 * min trade. leave it as is
        if (gte(baseWallet, lowThreshold) && gte(netCounterWalletInBase, lowThreshold)) {
            log.debug("{} >= {} && {} >= {}", baseWallet, lowThreshold, netCounterWalletInBase, lowThreshold);
            return;
        }

        BigDecimal grossCounterWalletInBase = counterWallet.divide(ticker.getAsk(), counterWallet.scale(), ROUNDING);
        BigDecimal total = baseWallet.add(grossCounterWalletInBase);
        if (lt(total, lowThreshold)) {
            log.warn("Total {} < {}", total, lowThreshold);
            return;
        }

        BigDecimal thresholdTotal = valueOf(4).multiply(minTrade);
        BigDecimal midThreshold = valueOf(3).multiply(minTrade);
        BigDecimal baseTarget;
        if (gte(total, thresholdTotal))
            baseTarget = total.divide(valueOf(2), ROUNDING);
        else if (gte(total, midThreshold))
            baseTarget = lowThreshold;
        else
            baseTarget = minTrade;

        if (eq(baseWallet, baseTarget)) {
            log.debug("base = target = {} && counter = {} ({})", baseWallet, counterWallet, grossCounterWalletInBase);
            return;
        }

        Order.OrderType type = gt(baseWallet, baseTarget) ? ASK : BID;
        BigDecimal amount = baseWallet.subtract(baseTarget).abs();

        LimitOrder order = new LimitOrder.Builder(type, pair).tradableAmount(amount).limitPrice(Tickers.getPrice(ticker, revert(type))).build();
        Map<String, BigDecimal> walletSim = Orders.simulateTrade(order, wallets, infos.get(market.exchange));
        log.info("Place {}", order);
        log.info("Simulated wallet:");
        describeWallet(walletSim, pair, ticker.getAsk(), feeFactor);
    }

    public void describeWallet(Map<String, BigDecimal> wallets, CurrencyPair pair, BigDecimal grossPrice, BigDecimal feeFactor) {
        BigDecimal netPrice = Orders.getNetPrice(grossPrice, BID, feeFactor);
        log.info("net bid price = {}", netPrice);

        String base = pair.baseSymbol;
        BigDecimal baseWallet = wallets.get(base);
        log.info("base {} = {}", base, baseWallet);
        BigDecimal counterWallet = wallets.get(pair.counterSymbol);
        BigDecimal netCounterWalletInBase = counterWallet.divide(netPrice, counterWallet.scale(), ROUNDING);
        log.info("cntr {} = {} ({} = {})", pair.counterSymbol, counterWallet, base, netCounterWalletInBase);

        // total if a transaction is made for all of the counter currency
        BigDecimal netTotal = netCounterWalletInBase.add(baseWallet);
        log.info("all  {} = {}", base, netTotal);
    }

    @Override
    public void destroy() {

    }
}
