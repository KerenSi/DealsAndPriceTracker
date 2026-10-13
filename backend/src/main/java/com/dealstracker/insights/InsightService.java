package com.dealstracker.insights;

import com.dealstracker.deals.ListingAnalysis;
import com.dealstracker.deals.PriceAnalysisService;
import com.dealstracker.domain.Category;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * Produces the home-page insights from the latest prices: real deals first, then "cheapest X is Y in Z"
 * for the categories / sizes of the products the user watches. At most {@link #MAX_INSIGHTS} in total;
 * suspicious sales are not shown.
 */
@Service
public class InsightService {

    public static final int MAX_INSIGHTS = 4;
    /** Slots kept for "cheapest" insights so a busy deals day doesn't hide them. */
    public static final int RESERVED_CHEAPEST = 2;

    private final PriceAnalysisService analysis;

    public InsightService(PriceAnalysisService analysis) {
        this.analysis = analysis;
    }

    /** @param watchedProductIds products the user marked as interesting; empty for anonymous users */
    public List<Insight> insights(Set<UUID> watchedProductIds) {
        return build(analysis.analyzeAll(), watchedProductIds);
    }

    static List<Insight> build(List<ListingAnalysis> all, Set<UUID> watchedProductIds) {
        List<Insight> deals = all.stream()
                .filter(a -> a.evaluation().genuineDeal())
                // Deals on watched products first, then the biggest drops.
                .sorted(Comparator.comparing((ListingAnalysis a) -> !watchedProductIds.contains(a.product().getId()))
                        .thenComparing(a -> a.evaluation().dropPercent(),
                                Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder())))
                .map(a -> new Insight("GENUINE_DEAL",
                        "Real deal: %s at %s is %s%% below its 30-day average (%s ILS)".formatted(
                                a.product().getTitle(), a.store().getBranchName(),
                                a.evaluation().dropPercent().toPlainString(),
                                a.evaluation().effectivePrice().toPlainString()),
                        a.product().getId(), a.store().getBranchName(), a.evaluation().effectivePrice(),
                        a.unitPrice(), a.unitLabel()))
                .toList();

        // Cheapest per unit, only within the category / size of a watched product
        // (diapers are only comparable within a size, formula within a stage).
        Set<String> watchedGroups = all.stream()
                .filter(a -> watchedProductIds.contains(a.product().getId()))
                .map(InsightService::groupKey)
                .collect(Collectors.toSet());
        Map<String, List<ListingAnalysis>> groups = all.stream()
                .filter(a -> a.unitPrice() != null)
                .filter(a -> watchedGroups.contains(groupKey(a)))
                .collect(Collectors.groupingBy(InsightService::groupKey, TreeMap::new, Collectors.toList()));
        List<Insight> cheapest = groups.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .min(Comparator.comparing(ListingAnalysis::unitPrice))
                        .map(a -> new Insight("CHEAPEST_PER_UNIT",
                                "Cheapest %s is %s at %s (%s ILS per %s)".formatted(
                                        e.getKey(), a.product().getBrand(), a.store().getBranchName(),
                                        a.unitPrice().toPlainString(), a.unitLabel()),
                                a.product().getId(), a.store().getBranchName(), a.evaluation().effectivePrice(),
                                a.unitPrice(), a.unitLabel()))
                        .stream())
                .toList();

        // Real deals on top; they leave room for up to RESERVED_CHEAPEST cheapest insights.
        int dealCount = Math.min(deals.size(), MAX_INSIGHTS - Math.min(cheapest.size(), RESERVED_CHEAPEST));
        return Stream.concat(deals.stream().limit(dealCount), cheapest.stream()).limit(MAX_INSIGHTS).toList();
    }

    private static String groupKey(ListingAnalysis a) {
        String base = a.product().getCategory() == Category.DIAPERS ? "diaper" : "baby food";
        if (a.product().getCategory() == Category.BABY_FOOD) {
            // Formula and purees aren't comparable per 100 g; group formula by stage only.
            return a.product().getSizeLabel() != null ? "formula (" + a.product().getSizeLabel() + ")" : base;
        }
        return a.product().getSizeLabel() != null ? base + " (" + a.product().getSizeLabel() + ")" : base;
    }
}
