package com.dealstracker.deals;

import com.dealstracker.config.TrackerProperties;
import com.dealstracker.domain.Category;
import com.dealstracker.domain.PriceSnapshot;
import com.dealstracker.domain.Product;
import com.dealstracker.domain.UnitType;
import com.dealstracker.repository.PriceSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Combines latest prices with their history to produce deal evaluations and unit prices. */
@Service
@Transactional(readOnly = true)
public class PriceAnalysisService {

    private final PriceSnapshotRepository snapshots;
    private final DealDetector detector;
    private final TrackerProperties props;
    private final Clock clock;

    public PriceAnalysisService(PriceSnapshotRepository snapshots, DealDetector detector, TrackerProperties props,
                                Clock clock) {
        this.snapshots = snapshots;
        this.detector = detector;
        this.props = props;
        this.clock = clock;
    }

    public List<ListingAnalysis> analyzeAll() {
        return analyze(snapshots.findLatestForAllListings(), props.deals().defaultThresholdPercent());
    }

    public List<ListingAnalysis> analyzeProduct(UUID productId, BigDecimal thresholdPercent) {
        return analyze(snapshots.findLatestForProduct(productId), thresholdPercent);
    }

    public List<ListingAnalysis> analyzeProduct(UUID productId) {
        return analyzeProduct(productId, props.deals().defaultThresholdPercent());
    }

    public List<PriceSnapshot> history(List<UUID> listingIds, int days) {
        return snapshots.findByListingIdInAndScrapedAtAfterOrderByScrapedAtAsc(
                listingIds, OffsetDateTime.now(clock).minusDays(days));
    }

    private List<ListingAnalysis> analyze(List<PriceSnapshot> latest, BigDecimal threshold) {
        if (latest.isEmpty()) {
            return List.of();
        }
        List<UUID> listingIds = latest.stream().map(s -> s.getListing().getId()).toList();
        OffsetDateTime since = OffsetDateTime.now(clock).minusDays(props.deals().longBaselineDays() + 1L);
        Map<UUID, List<PricePoint>> historyByListing = snapshots
                .findByListingIdInAndScrapedAtAfterOrderByScrapedAtAsc(listingIds, since).stream()
                .collect(Collectors.groupingBy(s -> s.getListing().getId(),
                        Collectors.mapping(PricePoint::from, Collectors.toList())));

        return latest.stream()
                .map(s -> {
                    Product product = s.getListing().getProduct();
                    DealEvaluation eval = detector.evaluate(PricePoint.from(s),
                            historyByListing.getOrDefault(s.getListing().getId(), List.of()), threshold);
                    return new ListingAnalysis(product, s.getListing().getStore(), s, eval,
                            unitPrice(product, eval.effectivePrice()), unitLabel(product));
                })
                .sorted(Comparator.comparing((ListingAnalysis a) -> a.evaluation().effectivePrice()))
                .toList();
    }

    static BigDecimal unitPrice(Product p, BigDecimal effectivePrice) {
        if (p.getUnitQuantity() == null || p.getUnitQuantity().signum() <= 0) {
            return null;
        }
        BigDecimal divisor = p.getUnitType() == UnitType.UNITS
                ? p.getUnitQuantity()
                : p.getUnitQuantity().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return effectivePrice.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    static String unitLabel(Product p) {
        if (p.getUnitType() == null) {
            return "unit";
        }
        return switch (p.getUnitType()) {
            case UNITS -> p.getCategory() == Category.DIAPERS ? "diaper" : "unit";
            case GRAMS -> "100g";
            case ML -> "100ml";
        };
    }
}
