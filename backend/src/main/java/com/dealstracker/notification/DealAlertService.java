package com.dealstracker.notification;

import com.dealstracker.deals.ListingAnalysis;
import com.dealstracker.deals.PriceAnalysisService;
import com.dealstracker.domain.AlertRule;
import com.dealstracker.domain.NotificationChannel;
import com.dealstracker.repository.AlertRuleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Evaluates active alert rules against the latest prices and notifies at most once per day per rule. */
@Service
public class DealAlertService {

    private static final Logger log = LoggerFactory.getLogger(DealAlertService.class);

    private final AlertRuleRepository rules;
    private final PriceAnalysisService analysis;
    private final Map<NotificationChannel, Notifier> notifiers;
    private final Clock clock;

    public DealAlertService(AlertRuleRepository rules, PriceAnalysisService analysis, List<Notifier> notifiers,
                            Clock clock) {
        this.rules = rules;
        this.analysis = analysis;
        this.notifiers = notifiers.stream().collect(Collectors.toMap(Notifier::channel, Function.identity()));
        this.clock = clock;
    }

    public int evaluateAndNotify() {
        int sent = 0;
        LocalDate today = LocalDate.now(clock);
        for (AlertRule rule : rules.findByActiveTrue()) {
            if (rule.getLastNotifiedAt() != null
                    && !rule.getLastNotifiedAt().atZoneSameInstant(clock.getZone()).toLocalDate().isBefore(today)) {
                continue;
            }
            List<ListingAnalysis> deals = analysis
                    .analyzeProduct(rule.getProduct().getId(), rule.getTargetDiscountPercentage()).stream()
                    .filter(a -> a.evaluation().genuineDeal())
                    .toList();
            if (deals.isEmpty()) {
                continue;
            }
            Notifier notifier = notifiers.get(rule.getNotificationChannel());
            if (notifier == null) {
                log.warn("No notifier for channel {} (rule {})", rule.getNotificationChannel(), rule.getId());
                continue;
            }
            try {
                notifier.send(rule.getChannelRecipientId(), format(deals));
                rule.setLastNotifiedAt(OffsetDateTime.now(clock));
                rules.save(rule);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send alert for rule {}", rule.getId(), e);
            }
        }
        return sent;
    }

    static String format(List<ListingAnalysis> deals) {
        ListingAnalysis best = deals.getFirst();
        StringBuilder sb = new StringBuilder("🔥 Price drop: ").append(best.product().getTitle()).append('\n');
        for (ListingAnalysis d : deals) {
            sb.append("• ").append(d.store().getBranchName()).append(": ")
                    .append(d.evaluation().effectivePrice().toPlainString()).append(" ILS (")
                    .append(d.evaluation().dropPercent().toPlainString()).append("% below 30-day avg)");
            if (d.latest().getPromotionDescription() != null) {
                sb.append(" - ").append(d.latest().getPromotionDescription());
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
