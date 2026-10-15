package com.dealstracker.notification.push;

import com.dealstracker.deals.ListingAnalysis;
import com.dealstracker.deals.PriceAnalysisService;
import com.dealstracker.domain.Interest;
import com.dealstracker.domain.ProductInterest;
import com.dealstracker.domain.PushDealNotification;
import com.dealstracker.domain.PushSubscription;
import com.dealstracker.repository.ProductInterestRepository;
import com.dealstracker.repository.PushDealNotificationRepository;
import com.dealstracker.repository.PushSubscriptionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pushes real deals on the products each user starred to every browser they turned notifications on in.
 * A deal is pushed once; it is pushed again only if the price drops further or a week has passed.
 */
@Service
public class DealPushService {

    static final Duration REPEAT_AFTER = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(DealPushService.class);

    /** What the service worker shows; see frontend/public/sw.js. */
    public record PushMessage(String title, String body, String url, String tag) {
    }

    private final PriceAnalysisService analysis;
    private final PushSubscriptionRepository subscriptions;
    private final ProductInterestRepository interests;
    private final PushDealNotificationRepository sent;
    private final WebPushSender sender;
    private final ObjectMapper json;
    private final Clock clock;

    public DealPushService(PriceAnalysisService analysis, PushSubscriptionRepository subscriptions,
                           ProductInterestRepository interests, PushDealNotificationRepository sent,
                           WebPushSender sender, ObjectMapper json, Clock clock) {
        this.analysis = analysis;
        this.subscriptions = subscriptions;
        this.interests = interests;
        this.sent = sent;
        this.sender = sender;
        this.json = json;
        this.clock = clock;
    }

    /** Returns the number of deal notifications delivered (one per user and product). */
    public int pushDeals() {
        Map<UUID, List<PushSubscription>> byUser = subscriptions.findAll().stream()
                .collect(Collectors.groupingBy(PushSubscription::getUserId));
        if (byUser.isEmpty()) {
            return 0;
        }
        List<ListingAnalysis> deals = analysis.analyzeAll().stream()
                .filter(a -> a.evaluation().genuineDeal())
                .toList();
        OffsetDateTime now = OffsetDateTime.now(clock);
        int delivered = 0;
        for (Map.Entry<UUID, List<PushSubscription>> user : byUser.entrySet()) {
            UUID userId = user.getKey();
            Set<UUID> watched = interests.findByIdUserId(userId).stream()
                    .filter(i -> i.getInterest() == Interest.WATCHED)
                    .map(i -> i.getId().productId())
                    .collect(Collectors.toSet());
            Map<UUID, PushDealNotification> previous = sent.findAllById(
                            watched.stream().map(p -> new ProductInterest.Key(userId, p)).toList()).stream()
                    .collect(Collectors.toMap(n -> n.getId().productId(), Function.identity()));
            for (ListingAnalysis deal : dealsToPush(deals, watched, previous, now)) {
                if (sendToAll(user.getValue(), message(deal)) > 0) {
                    record(userId, deal, previous.get(deal.product().getId()), now);
                    delivered++;
                }
            }
        }
        return delivered;
    }

    /**
     * How a test notification went. {@code NOT_REGISTERED} means the server has no subscription for that
     * browser (or it just expired and was forgotten), so the browser should subscribe again.
     */
    public record TestOutcome(Status status, int delivered, String detail) {
        public enum Status { DELIVERED, NOT_REGISTERED, FAILED }
    }

    static final PushMessage TEST_MESSAGE = new PushMessage("Deal alerts are on",
            "You'll get a notification here when a starred product has a real deal.", "/", "test");

    /**
     * Sends a sample notification to one of the user's browsers, identified by its push endpoint, or to all
     * of them when {@code endpoint} is null.
     */
    public TestOutcome sendTest(UUID userId, String endpoint) {
        List<PushSubscription> targets = endpoint == null
                ? subscriptions.findByUserId(userId)
                : subscriptions.findByEndpoint(endpoint).filter(s -> s.getUserId().equals(userId)).stream().toList();
        if (targets.isEmpty()) {
            return new TestOutcome(TestOutcome.Status.NOT_REGISTERED, 0, null);
        }
        String payload = payload(TEST_MESSAGE);
        int delivered = 0;
        int gone = 0;
        String problem = null;
        for (PushSubscription subscription : targets) {
            WebPushSender.Delivery delivery = deliver(subscription, payload);
            switch (delivery.result()) {
                case DELIVERED -> delivered++;
                case GONE -> gone++;
                case FAILED -> problem = delivery.detail();
            }
        }
        if (delivered > 0) {
            return new TestOutcome(TestOutcome.Status.DELIVERED, delivered, null);
        }
        if (problem == null && gone > 0) {
            return new TestOutcome(TestOutcome.Status.NOT_REGISTERED, 0, null);
        }
        return new TestOutcome(TestOutcome.Status.FAILED, 0, problem);
    }

    /** For each watched product with a real deal, its best deal, unless that deal was already pushed. */
    static List<ListingAnalysis> dealsToPush(List<ListingAnalysis> deals, Set<UUID> watched,
                                             Map<UUID, PushDealNotification> previous, OffsetDateTime now) {
        Map<UUID, ListingAnalysis> best = new LinkedHashMap<>();
        deals.stream()
                .filter(a -> watched.contains(a.product().getId()))
                .sorted(Comparator.comparing(a -> a.evaluation().effectivePrice()))
                .forEach(a -> best.putIfAbsent(a.product().getId(), a));
        return best.values().stream()
                .filter(a -> isNew(a, previous.get(a.product().getId()), now))
                .toList();
    }

    private static boolean isNew(ListingAnalysis deal, PushDealNotification last, OffsetDateTime now) {
        return last == null
                || deal.evaluation().effectivePrice().compareTo(last.getEffectivePrice()) < 0
                || last.getNotifiedAt().isBefore(now.minus(REPEAT_AFTER));
    }

    static PushMessage message(ListingAnalysis deal) {
        BigDecimal price = deal.evaluation().effectivePrice();
        String body = "%s: ₪%s, %s%% below its 30-day average".formatted(deal.store().getBranchName(),
                price.toPlainString(), deal.evaluation().dropPercent().toPlainString());
        if (deal.latest().getPromotionDescription() != null) {
            body += " (" + deal.latest().getPromotionDescription() + ")";
        }
        UUID productId = deal.product().getId();
        return new PushMessage("Real deal: " + deal.product().getTitle(), body, "/products/" + productId,
                "deal-" + productId);
    }

    private int sendToAll(List<PushSubscription> targets, PushMessage message) {
        String payload = payload(message);
        int delivered = 0;
        for (PushSubscription subscription : targets) {
            if (deliver(subscription, payload).result() == WebPushSender.Result.DELIVERED) {
                delivered++;
            }
        }
        return delivered;
    }

    /** Sends one message, forgetting the subscription if the browser has dropped it. */
    private WebPushSender.Delivery deliver(PushSubscription subscription, String payload) {
        WebPushSender.Delivery delivery = sender.send(subscription, payload);
        if (delivery.result() == WebPushSender.Result.GONE) {
            log.info("Removing expired push subscription {}", subscription.getId());
            subscriptions.delete(subscription);
        }
        return delivery;
    }

    private String payload(PushMessage message) {
        try {
            return json.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void record(UUID userId, ListingAnalysis deal, PushDealNotification last, OffsetDateTime now) {
        BigDecimal price = deal.evaluation().effectivePrice();
        if (last == null) {
            sent.save(new PushDealNotification(new ProductInterest.Key(userId, deal.product().getId()), price, now));
        } else {
            last.update(price, now);
            sent.save(last);
        }
    }
}
