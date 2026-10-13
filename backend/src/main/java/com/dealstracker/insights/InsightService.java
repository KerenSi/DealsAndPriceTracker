package com.dealstracker.insights;

import com.dealstracker.deals.ListingAnalysis;
import com.dealstracker.deals.PriceAnalysisService;
import com.dealstracker.domain.Category;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Produces "cheapest X is Y in Z" style insights from the latest prices. */
@Service
public class InsightService {

    private final PriceAnalysisService analysis;

    public InsightService(PriceAnalysisService analysis) {
        this.analysis = analysis;
    }

    public List<Insight> insights() {
        List<ListingAnalysis> all = analysis.analyzeAll();
        List<Insight> out = new ArrayList<>();

        // Cheapest per unit in each category / size (diapers are only comparable within a size).
        Map<String, List<ListingAnalysis>> groups = all.stream()
                .filter(a -> a.unitPrice() != null)
                .collect(Collectors.groupingBy(InsightService::groupKey, TreeMap::new, Collectors.toList()));
        groups.forEach((key, items) -> items.stream()
                .min(Comparator.comparing(ListingAnalysis::unitPrice))
                .ifPresent(a -> out.add(new Insight("CHEAPEST_PER_UNIT",
                        "Cheapest %s is %s at %s (%s ILS per %s)".formatted(
                                key, a.product().getBrand(), a.store().getBranchName(),
                                a.unitPrice().toPlainString(), a.unitLabel()),
                        a.product().getId(), a.store().getBranchName(), a.evaluation().effectivePrice(),
                        a.unitPrice(), a.unitLabel()))));

        all.stream()
                .filter(a -> a.evaluation().genuineDeal())
                .sorted(Comparator.comparing((ListingAnalysis a) -> a.evaluation().dropPercent()).reversed())
                .forEach(a -> out.add(new Insight("GENUINE_DEAL",
                        "Real deal: %s at %s is %s%% below its 30-day average (%s ILS)".formatted(
                                a.product().getTitle(), a.store().getBranchName(),
                                a.evaluation().dropPercent().toPlainString(),
                                a.evaluation().effectivePrice().toPlainString()),
                        a.product().getId(), a.store().getBranchName(), a.evaluation().effectivePrice(),
                        a.unitPrice(), a.unitLabel())));

        all.stream()
                .filter(a -> a.evaluation().suspectedFakeDiscount())
                .forEach(a -> out.add(new Insight("FAKE_DISCOUNT",
                        "Suspicious sale: %s at %s - %s, real saving vs. 30-day average is %s%%".formatted(
                                a.product().getTitle(), a.store().getBranchName(),
                                a.evaluation().claimedDiscountPercent() == null
                                        ? "advertised as a promotion"
                                        : "advertised " + a.evaluation().claimedDiscountPercent() + "% off",
                                Objects.toString(a.evaluation().dropPercent(), "?")),
                        a.product().getId(), a.store().getBranchName(), a.evaluation().effectivePrice(),
                        a.unitPrice(), a.unitLabel())));
        return out;
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
