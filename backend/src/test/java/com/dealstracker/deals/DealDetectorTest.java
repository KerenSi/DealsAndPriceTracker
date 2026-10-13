package com.dealstracker.deals;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DealDetectorTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 1, 6, 0, 0, 0, ZoneOffset.ofHours(3));
    private static final BigDecimal THRESHOLD = new BigDecimal("15");

    private final DealDetector detector = new DealDetector(30, 60);

    private static List<PricePoint> steadyHistory(String price, int days) {
        List<PricePoint> out = new ArrayList<>();
        for (int d = days; d >= 1; d--) {
            out.add(PricePoint.of(new BigDecimal(price), NOW.minusDays(d)));
        }
        return out;
    }

    @Test
    void genuineDropAboveThresholdIsADeal() {
        PricePoint current = new PricePoint(new BigDecimal("75.00"), new BigDecimal("100.00"), true, null, null, NOW);

        DealEvaluation eval = detector.evaluate(current, steadyHistory("100.00", 30), THRESHOLD);

        assertThat(eval.genuineDeal()).isTrue();
        assertThat(eval.dropPercent()).isEqualByComparingTo("25.0");
        assertThat(eval.baselinePrice()).isEqualByComparingTo("100.00");
        assertThat(eval.suspectedFakeDiscount()).isFalse();
    }

    @Test
    void smallDropIsIgnored() {
        PricePoint current = PricePoint.of(new BigDecimal("92.00"), NOW);

        DealEvaluation eval = detector.evaluate(current, steadyHistory("100.00", 30), THRESHOLD);

        assertThat(eval.genuineDeal()).isFalse();
        assertThat(eval.dropPercent()).isEqualByComparingTo("8.0");
    }

    @Test
    void inflatedReferencePriceIsFlaggedAsFake() {
        // Advertised "was 140, now 98" but it was always ~100.
        PricePoint current = new PricePoint(new BigDecimal("98.00"), new BigDecimal("140.00"), true, null, null, NOW);

        DealEvaluation eval = detector.evaluate(current, steadyHistory("100.00", 30), THRESHOLD);

        assertThat(eval.genuineDeal()).isFalse();
        assertThat(eval.suspectedFakeDiscount()).isTrue();
        assertThat(eval.claimedDiscountPercent()).isEqualByComparingTo("30.0");
    }

    @Test
    void priceRaisedRightBeforeMarkdownIsFlagged() {
        List<PricePoint> history = new ArrayList<>(steadyHistory("100.00", 30).subList(0, 25));
        // Last 5 days: raised to 130.
        for (int d = 5; d >= 1; d--) {
            history.add(PricePoint.of(new BigDecimal("130.00"), NOW.minusDays(d)));
        }
        PricePoint current = new PricePoint(new BigDecimal("99.00"), new BigDecimal("130.00"), true, null, null, NOW);

        DealEvaluation eval = detector.evaluate(current, history, THRESHOLD);

        assertThat(eval.suspectedFakeDiscount()).isTrue();
        assertThat(eval.genuineDeal()).isFalse();
    }

    @Test
    void multiBuyUsesPerUnitPrice() {
        // "2 for 150" on a 100 ILS item = 75 per unit.
        PricePoint current = new PricePoint(new BigDecimal("100.00"), null, true, 2, new BigDecimal("150.00"), NOW);

        DealEvaluation eval = detector.evaluate(current, steadyHistory("100.00", 30), THRESHOLD);

        assertThat(eval.effectivePrice()).isEqualByComparingTo("75.00");
        assertThat(eval.genuineDeal()).isTrue();
        assertThat(eval.reasons()).anyMatch(r -> r.startsWith("Multi-buy"));
    }

    @Test
    void notEnoughHistoryIsNeverADeal() {
        PricePoint current = PricePoint.of(new BigDecimal("50.00"), NOW);

        DealEvaluation eval = detector.evaluate(current, steadyHistory("100.00", 2), THRESHOLD);

        assertThat(eval.genuineDeal()).isFalse();
        assertThat(eval.baselinePrice()).isNull();
    }

    @Test
    void recurringPromotionsAreAlreadyInTheBaseline() {
        // Item alternates between 100 and 80 - 80 is its normal promo price, not a special deal.
        List<PricePoint> history = new ArrayList<>();
        for (int d = 30; d >= 1; d--) {
            history.add(PricePoint.of(new BigDecimal(d % 2 == 0 ? "100.00" : "80.00"), NOW.minusDays(d)));
        }
        PricePoint current = PricePoint.of(new BigDecimal("80.00"), NOW);

        DealEvaluation eval = detector.evaluate(current, history, THRESHOLD);

        assertThat(eval.genuineDeal()).isFalse();
    }
}
