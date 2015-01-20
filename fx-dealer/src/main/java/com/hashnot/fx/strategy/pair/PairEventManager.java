package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.BestOfferEvent;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.framework.OrderBookSideUpdateEvent;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.hashnot.xchange.ext.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author Rafał Krupiński
 */
public class PairEventManager implements Listener, Runnable {
    final private static Logger log = LoggerFactory.getLogger(PairEventManager.class);

    final private BlockingDeque<EventWrapper> queue = new LinkedBlockingDeque<>();

    final private Dealer dealer;

    @Override
    public void updateBestOffer(BestOfferEvent event) {
        addEvent(EventType.BestOffer, event);
    }

    private void addEvent(EventType type, Event event) {
        log.debug("{}", type);
        EventWrapper evt = new EventWrapper(type, event);
        int size;
        boolean success;
        synchronized (queue) {
            success = queue.offer(evt);
            size = queue.size();
        }
        if (!success)
            log.warn("Could not add element {}", evt.event);
        log.debug("Queue size={}", size);
    }

    @Override
    public void orderBookSideChanged(OrderBookSideUpdateEvent event) {
        addEvent(EventType.OrderBook, event);
    }

    @Override
    public void trade(UserTradeEvent event) {
        addEvent(EventType.Trade, event);
    }

    @Override
    public Consumer<Future<Boolean>> onCancel() {
        return f -> addEventHead(EventType.Cancel, f);
    }

    @Override
    public Consumer<Future<String>> onOpen() {
        return f -> addEventHead(EventType.Open, f);
    }

    public void addEventHead(EventType type, Object event) {
        EventWrapper evt = new EventWrapper(type, event);
        int size;
        boolean success;
        synchronized (queue) {
            success = queue.offerFirst(evt);
            size = queue.size();
        }
        if (!success)
            log.warn("Could not add element {}", evt.event);
        log.debug("Queue size={}", size);
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (Thread.interrupted()) {
                    log.debug("Interrupted");
                    return;
                }
                EventWrapper event = queue.take();
                List<EventWrapper> all = allOfType(event);
                switch (event.type) {
                    case BestOffer:
                        handleBestOffer(all);
                        break;
                    case OrderBook:
                        handleOrderBook(all);
                        break;
                    case Trade:
                        handleTrade(all);
                        break;
                    case Open:
                        handleOpen(all);
                        break;
                    case Cancel:
                        handleCancel(all);
                        break;
                    default:
                        throw new IllegalArgumentException(event.toString());
                }
            }
        } catch (InterruptedException e) {
            if (log.isDebugEnabled())
                log.warn("Interrupted!", e);
            else
                log.warn("Interrupted!");
        } catch (Error e) {
            log.error("Error in {}", this, e);
            throw e;
        } catch (RuntimeException e) {
            log.warn("Error in {}", this, e);
        }
    }

    private List<EventWrapper> allOfType(EventWrapper event) {
        List<EventWrapper> taken = new LinkedList<>();
        synchronized (queue) {
            for (EventWrapper e2 : queue) {
                if (e2.type == event.type)
                    taken.add(e2);
            }
            queue.removeAll(taken);
        }
        taken.add(0, event);
        return taken;
    }

    private void handleTrade(List<EventWrapper> userTradeEvents) {
        List<UserTradeEvent> events = userTradeEvents.stream().map(e -> (UserTradeEvent) e.event).collect(Collectors.toList());
        dealer.trades(events);
    }

    private void handleOrderBook(List<EventWrapper> orderBookEvents) {
        Map<MarketSide, OrderBookSideUpdateEvent> events = new LinkedHashMap<>();
        for (EventWrapper event : orderBookEvents) {
            OrderBookSideUpdateEvent e = (OrderBookSideUpdateEvent) event.event;
            events.put(e.source, e);
        }
        dealer.onOrderBookSideEvents(events.values());
    }

    private void handleBestOffer(List<EventWrapper> bestOfferEvents) {
        Map<MarketSide, BestOfferEvent> events = new LinkedHashMap<>();
        for (EventWrapper event : bestOfferEvents) {
            BestOfferEvent e = (BestOfferEvent) event.event;
            events.put(e.source, e);
        }
        dealer.updateBestOffers(events.values());
    }

    private void handleOpen(List<EventWrapper> events) {
        for (EventWrapper event : events) {
            @SuppressWarnings("unchecked")
            Future<String> f = (Future<String>) event.event;
            dealer.handleOpened(f);
        }
    }

    private void handleCancel(List<EventWrapper> events) {
        for (EventWrapper event : events) {
            @SuppressWarnings("unchecked")
            Future<Boolean> f = (Future<Boolean>) event.event;
            dealer.handleCanceled(f);
        }
    }

    public PairEventManager(Dealer dealer) {
        this.dealer = dealer;
    }

    static enum EventType {
        BestOffer,
        OrderBook,
        Trade,
        Cancel,
        Open
    }

    static class EventWrapper {
        final public EventType type;
        final public Object event;

        EventWrapper(EventType type, Object event) {
            this.type = type;
            this.event = event;
        }

        public Object getEvent() {
            return event;
        }
    }

    @Override
    public String toString() {
        return dealer.toString();
    }
}
