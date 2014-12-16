package com.hashnot.fx.framework;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;
import com.hashnot.fx.util.ConfigurableThreadFactory;
import com.hashnot.xchange.event.IExchangeMonitor;
import com.hashnot.xchange.ext.util.MDCRunnable;
import com.xeiam.xchange.currency.CurrencyPair;
import groovy.lang.GroovyShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * @author Rafał Krupiński
 */
public class Main {
    final private static Logger log = LoggerFactory.getLogger(Main.class);
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
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ScheduledExecutorService.class).toInstance(scheduler);
                bind(Runnable.class).annotatedWith(Names.named("exitHook")).toInstance(framework::stop);
                bind(MBeanServer.class).toProvider(ManagementFactory::getPlatformMBeanServer);
            }
        });
        injector.injectMembers(strategy);

        framework.start();
    }

    protected static ScheduledExecutorService scheduler() {
        ThreadFactory tf = new ConfigurableThreadFactory();
        return new ScheduledThreadPoolExecutor(10, tf) {
            @Override
            public void execute(Runnable command) {
                super.execute(new MDCRunnable(command));
            }
        };
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
            strategy.init(monitors, Arrays.asList(pair));
        } catch (Exception e) {
            stop();
        }
    }

    public void stop() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
            // thrown during shutdown - safe to ignore
        }
        CountDownLatch countDownLatch = new CountDownLatch(monitors.size());
        monitors.forEach(monitor -> scheduler.execute(() -> {
            monitor.stop();
            countDownLatch.countDown();
        }));
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.warn("Interrupted!", e);
        }
        scheduler.shutdownNow();
        log.info("Good bye");
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
