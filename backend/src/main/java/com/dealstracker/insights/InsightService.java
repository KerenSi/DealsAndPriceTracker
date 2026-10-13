package com.dealstracker.insights;

import com.dealstracker.auth.UserPreferences;
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
 * Produces the home-page insights from the latest prices: every real deal first, then "cheapest X is Y in Z"
 * for the categories / sizes of the products the user watches. Products the user marked as not interesting
 * are left out entirely, and suspicious sales are not shown.
 */
@Service
public class InsightService {

    /** Aim for this many insights; more only when there are more real deals than that. */
    public static final int TARGET_INSIGHTS = 4;
    /** "Cheapest" insights shown even on a day with many deals. */
    public static final int MIN_CHEAPEST = 2;

    private final PriceAnalysisService analysis;

    public InsightService(PriceAnalysisService analysis) {
        this.analysis = analysis;
    }

    public List<Insight> insights(UserPreferences prefs) {
        return build(analysis.analyzeAll(), prefs);
    }

    static List<Insight> build(List<ListingAnalysis> analyses, UserPreferences prefs) {
        Set<UUID> watched = prefs.watched();
        Set<UUID> notInterested = prefs.notInterested();
        List<ListingAnalysis> all = analyses.stream()
                .filter(a -> !notInterested.contains(a.product().getId()))
                .toList();

        List<Insight> deals = all.stream()
                .filter(a -> a.evaluation().genuineDeal())
                // Deals on watched products first, then the biggest drops.
                .sorted(Comparator.comparing((ListingAnalysis a) -> !watched.contains(a.product().getId()))
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

        // Cheapest per unit within the category / size of a watched product (diapers are only comparable
        // within a size, formula within a stage). Without "show similar products" only watched products compete.
        Set<String> watchedGroups = all.stream()
                .filter(a -> watched.contains(a.product().getId()))
                .map(InsightService::groupKey)
                .collect(Collectors.toSet());
        Map<String, List<ListingAnalysis>> groups = all.stream()
                .filter(a -> a.unitPrice() != null)
                .filter(a -> prefs.showSimilarProducts() || watched.contains(a.product().getId()))
                .filter(a -> watchedGroups.contains(groupKey(a)))
                .collect(Collectors.groupingBy(InsightService::groupKey, TreeMap::new, Collectors.toList()));
        int cheapestCount = Math.max(MIN_CHEAPEST, TARGET_INSIGHTS - deals.size());
        Stream<Insight> cheapest = groups.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .min(Comparator.comparing(ListingAnalysis::unitPrice))
                        .map(a -> new Insight("CHEAPEST_PER_UNIT",
                                "Cheapest %s is %s at %s (%s ILS per %s)".formatted(
                                        e.getKey(), a.product().getBrand(), a.store().getBranchName(),
                                        a.unitPrice().toPlainString(), a.unitLabel()),
                                a.product().getId(), a.store().getBranchName(), a.evaluation().effectivePrice(),
                                a.unitPrice(), a.unitLabel()))
                        .stream())
                .limit(cheapestCount);

        return Stream.concat(deals.stream(), cheapest).toList();
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
