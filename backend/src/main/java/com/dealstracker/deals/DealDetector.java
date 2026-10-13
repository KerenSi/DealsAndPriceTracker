package com.dealstracker.deals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based detector for genuine price drops and fake ("anchored") discounts.
 *
 * <ul>
 *   <li>Baseline: moving average of effective prices over the last N days (30 / 60).</li>
 *   <li>Threshold: drops smaller than the threshold (default 15%) are ignored as noise.</li>
 *   <li>Anchoring: the advertised "original" price is higher than anything we actually observed,
 *       or the shelf price was raised shortly before the markdown.</li>
 *   <li>Multi-buy: "1+1" / "3 for 50" offers are converted to a per-package price first.</li>
 * </ul>
 */
public class DealDetector {

    static final int MIN_HISTORY_POINTS = 3;
    /** Prices within this tolerance of each other are treated as equal. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05");
    private static final Duration RECENT_WINDOW = Duration.ofDays(7);
    /** A regular-price increase of more than this in the last week counts as "raised before the sale". */
    private static final BigDecimal RAISE_THRESHOLD = new BigDecimal("0.10");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final int baselineDays;
    private final int longBaselineDays;

    public DealDetector(int baselineDays, int longBaselineDays) {
        this.baselineDays = baselineDays;
        this.longBaselineDays = longBaselineDays;
    }

    /**
     * @param current          the latest observation
     * @param history          earlier observations (any order); points at or after {@code current} are ignored
     * @param thresholdPercent minimum real drop, in percent, to call it a deal
     */
    public DealEvaluation evaluate(PricePoint current, List<PricePoint> history, BigDecimal thresholdPercent) {
        OffsetDateTime now = current.observedAt();
        List<PricePoint> past = history.stream()
                .filter(p -> p.observedAt().isBefore(now))
                .toList();
        List<PricePoint> shortWindow = within(past, now, Duration.ofDays(baselineDays));
        List<PricePoint> longWindow = within(past, now, Duration.ofDays(longBaselineDays));

        BigDecimal effective = current.effectivePrice();
        List<String> reasons = new ArrayList<>();
        if (current.isMultiBuy()) {
            reasons.add("Multi-buy offer: %d for %s ILS = %s ILS per unit"
                    .formatted(current.promoMinQuantity(), current.promoTotalPrice().toPlainString(), money(effective)));
        }

        BigDecimal claimed = null;
        if (current.referencePrice() != null && current.referencePrice().signum() > 0) {
            claimed = percentDrop(current.referencePrice(), effective);
        }

        if (shortWindow.size() < MIN_HISTORY_POINTS) {
            reasons.add("Not enough price history yet (%d observations in the last %d days)"
                    .formatted(shortWindow.size(), baselineDays));
            return new DealEvaluation(false, scale(effective), null, average(longWindow), null, claimed, false, reasons);
        }

        BigDecimal baseline = average(shortWindow);
        BigDecimal longBaseline = average(longWindow);
        BigDecimal drop = percentDrop(baseline, effective);

        boolean anchored = detectAnchoring(current, shortWindow, now, reasons);

        boolean bigEnough = drop.compareTo(thresholdPercent) >= 0;
        if (bigEnough) {
            reasons.add("Price is %s%% below the %d-day average of %s ILS"
                    .formatted(drop.toPlainString(), baselineDays, money(baseline)));
        } else {
            reasons.add("Drop of %s%% vs. the %d-day average is below the %s%% threshold"
                    .formatted(drop.toPlainString(), baselineDays, thresholdPercent.stripTrailingZeros().toPlainString()));
        }
        if (claimed != null && claimed.compareTo(thresholdPercent) >= 0 && !bigEnough) {
            reasons.add("Store advertises %s%% off, but the real saving vs. history is only %s%%"
                    .formatted(claimed.toPlainString(), drop.toPlainString()));
            anchored = true;
        }

        return new DealEvaluation(bigEnough && !anchored, scale(effective), baseline, longBaseline, drop, claimed,
                anchored, reasons);
    }

    private boolean detectAnchoring(PricePoint current, List<PricePoint> window, OffsetDateTime now,
                                    List<String> reasons) {
        if (!current.promotion() && current.referencePrice() == null) {
            return false;
        }
        // Compare against regular (non-promotional) shelf prices; fall back to everything if all were promos.
        List<PricePoint> regular = window.stream().filter(p -> !p.promotion()).toList();
        if (regular.isEmpty()) {
            regular = window;
        }

        boolean anchored = false;
        BigDecimal maxRegular = regular.stream().map(PricePoint::price).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal ref = current.referencePrice();
        if (ref != null && ref.compareTo(maxRegular.multiply(BigDecimal.ONE.add(TOLERANCE))) > 0) {
            reasons.add("Advertised original price %s ILS is higher than any regular price in the last %d days (max %s ILS)"
                    .formatted(money(ref), baselineDays, money(maxRegular)));
            anchored = true;
        }

        OffsetDateTime recentFrom = now.minus(RECENT_WINDOW);
        List<BigDecimal> recent = regular.stream()
                .filter(p -> !p.observedAt().isBefore(recentFrom)).map(PricePoint::price).toList();
        List<BigDecimal> older = regular.stream()
                .filter(p -> p.observedAt().isBefore(recentFrom)).map(PricePoint::price).toList();
        if (!recent.isEmpty() && !older.isEmpty()) {
            BigDecimal recentMedian = median(recent);
            BigDecimal olderMedian = median(older);
            if (recentMedian.compareTo(olderMedian.multiply(BigDecimal.ONE.add(RAISE_THRESHOLD))) > 0
                    && current.effectivePrice().compareTo(olderMedian.multiply(BigDecimal.ONE.subtract(TOLERANCE))) >= 0) {
                reasons.add("Shelf price was raised to %s ILS in the week before this markdown (usually %s ILS)"
                        .formatted(money(recentMedian), money(olderMedian)));
                anchored = true;
            }
        }
        return anchored;
    }

    private static BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1
                ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private static List<PricePoint> within(List<PricePoint> points, OffsetDateTime now, Duration window) {
        OffsetDateTime from = now.minus(window);
        return points.stream().filter(p -> !p.observedAt().isBefore(from)).toList();
    }

    private static BigDecimal average(List<PricePoint> points) {
        if (points.isEmpty()) {
            return null;
        }
        BigDecimal sum = points.stream().map(PricePoint::effectivePrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(points.size()), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal percentDrop(BigDecimal from, BigDecimal to) {
        return from.subtract(to).multiply(HUNDRED).divide(from, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String money(BigDecimal v) {
        return scale(v).toPlainString();
    }
}
