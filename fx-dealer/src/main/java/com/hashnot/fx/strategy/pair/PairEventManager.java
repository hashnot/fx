package com.hashnot.fx.strategy.pair;

import com.hashnot.fx.framework.BestOfferEvent;
import com.hashnot.fx.framework.MarketSide;
import com.hashnot.fx.framework.OrderBookSideUpdateEvent;
import com.hashnot.xchange.event.trade.UserTradeEvent;
import com.hashnot.xchange.ext.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

/**
 * @author Rafał Krupiński
 */
public class PairEventManager implements Listener, Runnable {
    final private static Logger log = LoggerFactory.getLogger(PairEventManager.class);
    final private BlockingQueue<EventWrapper> queue = new LinkedBlockingQueue<>();
    final private Dealer dealer;

    @Override
    public void updateBestOffer(BestOfferEvent event) {
        EventWrapper evt = new EventWrapper(EventType.BestOffer, event);
        addEvent(evt);
    }

    private void addEvent(EventWrapper evt) {
        int size;
        synchronized (queue) {
            queue.offer(evt);
            size = queue.size();
        }
        log.debug("Queue size={}", size);
    }

    @Override
    public void orderBookSideChanged(OrderBookSideUpdateEvent event) {
        EventWrapper evt = new EventWrapper(EventType.OrderBook, event);
        addEvent(evt);
    }

    @Override
    public void trade(UserTradeEvent event) {
        EventWrapper evt = new EventWrapper(EventType.Trade, event);
        addEvent(evt);
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
                    default:
                        throw new IllegalArgumentException(event.toString());
                }
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted!", e);
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
        dealer.orderBookSideChanged((OrderBookSideUpdateEvent) orderBookEvents.get(orderBookEvents.size() - 1).event);
    }

    private void handleBestOffer(List<EventWrapper> bestOfferEvents) {
        Map<MarketSide, BestOfferEvent> events = bestOfferEvents.stream().map(e -> (BestOfferEvent) e.event).collect(toMap(e -> e.source, e -> e));
        dealer.updateBestOffers(events.values());
    }

    public PairEventManager(Dealer dealer) {
        this.dealer = dealer;
    }

    static enum EventType {
        BestOffer,
        OrderBook,
        Trade
    }

    static class EventWrapper {
        final public EventType type;
        final public Event event;

        EventWrapper(EventType type, Event event) {
            this.type = type;
            this.event = event;
        }

        public Event getEvent() {
            return event;
        }
    }

    @Override
    public String toString() {
        return dealer.toString();
    }
}
