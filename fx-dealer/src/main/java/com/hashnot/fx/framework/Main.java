package com.hashnot.fx.framework;

import com.hashnot.fx.util.ConfigurableThreadFactory;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.trade.ITradeService;
import com.xeiam.xchange.currency.CurrencyPair;
import groovy.lang.GroovyShell;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * @author Rafał Krupiński
 */
public class Main {
    private static final CurrencyPair pair = CurrencyPair.BTC_EUR;
    private static final String DEFAULT_STRATEGY = "com.hashnot.fx.strategy.pair.PairStrategy";
    private Thread shutdownHook = new Thread(this::stop, "Shutdown");

    public static void main(String[] args) throws IOException, InterruptedException {
        Thread.setDefaultUncaughtExceptionHandler(ConfigurableThreadFactory.exceptionHandler);

        ScheduledExecutorService scheduler = scheduler();
        Collection<IExchangeMonitor> monitors = loadExchanges(args[0], scheduler);

        String strategyName = args.length > 1 ? args[1] : DEFAULT_STRATEGY;
        IStrategy strategy = getPairStrategy(strategyName);

        Main framework = new Main(strategy, monitors, scheduler);
        framework.start();
    }

    protected static ScheduledExecutorService scheduler() {
        ThreadFactory tf = new ConfigurableThreadFactory();
        return Executors.newScheduledThreadPool(10, tf);
    }

    private Collection<IExchangeMonitor> monitors;

    private ScheduledExecutorService scheduler;
    private IStrategy strategy;

    private Main(IStrategy strategy, Collection<IExchangeMonitor> monitors, ScheduledExecutorService scheduler) {
        this.strategy = strategy;
        this.monitors = monitors;
        this.scheduler = scheduler;
    }

    public void start() {
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        for (IExchangeMonitor monitor : monitors) {
            monitor.start();
        }
        try {
            strategy.init(monitors, Arrays.asList(pair), this::stop);
        } catch (Exception e) {
            stop();
        }
    }

    public void stop() {
        for (IExchangeMonitor monitor : monitors) {
            ITradeService tradeService = monitor.getExchange().getPollingTradeService();
            tradeService.cancelAll();
            monitor.stop();
        }
        scheduler.shutdown();
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
            // thrown during shutdown - safe to ignore
        }
    }

    protected static IStrategy getPairStrategy(String className) {
        try {
            return (IStrategy) Class.forName(className).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Collection<IExchangeMonitor> loadExchanges(String path, ScheduledExecutorService scheduler) {
        GroovyShell sh = new GroovyShell();
        sh.setVariable("executor", scheduler);
        try {
            return (Collection<IExchangeMonitor>) sh.evaluate(new File(path));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not load setup script", e);
        }
    }

}
