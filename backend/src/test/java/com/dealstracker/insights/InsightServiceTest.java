package com.dealstracker.insights;

import static org.assertj.core.api.Assertions.assertThat;

import com.dealstracker.deals.DealEvaluation;
import com.dealstracker.deals.ListingAnalysis;
import com.dealstracker.domain.Category;
import com.dealstracker.domain.Product;
import com.dealstracker.domain.Store;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InsightServiceTest {

    private static final Store STORE_A = new Store("YOCHANANOF", "Yochananof Yad Eliyahu", "Tel Aviv");
    private static final Store STORE_B = new Store("OSHER_AD", "Osher Ad Yad Eliyahu", "Tel Aviv");

    private static Product product(String title, Category category, String size) {
        Product p = new Product(UUID.randomUUID().toString(), title, category);
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        p.setBrand(title.split(" ")[0]);
        p.setSizeLabel(size);
        return p;
    }

    private static ListingAnalysis listing(Product p, Store s, String unitPrice, boolean deal, String drop,
                                           boolean fake) {
        DealEvaluation eval = new DealEvaluation(deal, new BigDecimal("50.00"), new BigDecimal("60.00"), null,
                drop == null ? null : new BigDecimal(drop), fake ? new BigDecimal("40") : null, fake, List.of());
        return new ListingAnalysis(p, s, null, eval, new BigDecimal(unitPrice), "diaper");
    }

    private static ListingAnalysis plain(Product p, Store s, String unitPrice) {
        return listing(p, s, unitPrice, false, "2", false);
    }

    @Test
    void suspiciousSalesAreNotShown() {
        Product huggies = product("Huggies Size 4", Category.DIAPERS, "Size 4");
        List<ListingAnalysis> all = List.of(listing(huggies, STORE_A, "1.20", false, "3", true));

        assertThat(InsightService.build(all, Set.of(huggies.getId())))
                .extracting(Insight::type)
                .doesNotContain("FAKE_DISCOUNT");
    }

    @Test
    void realDealsComeFirstThenCheapest() {
        Product huggies = product("Huggies Size 4", Category.DIAPERS, "Size 4");
        Product pampers = product("Pampers Size 4", Category.DIAPERS, "Size 4");
        List<ListingAnalysis> all = List.of(
                plain(huggies, STORE_A, "1.10"),
                listing(pampers, STORE_B, "1.30", true, "20", false));

        List<Insight> insights = InsightService.build(all, Set.of(huggies.getId()));

        assertThat(insights).extracting(Insight::type).containsExactly("GENUINE_DEAL", "CHEAPEST_PER_UNIT");
        assertThat(insights.get(1).message()).startsWith("Cheapest diaper (Size 4) is Huggies");
    }

    @Test
    void cheapestOnlyCoversGroupsOfWatchedProducts() {
        Product stage1 = product("Materna Stage 1", Category.BABY_FOOD, "Stage 1");
        Product stage2 = product("Materna Stage 2", Category.BABY_FOOD, "Stage 2");
        Product size4 = product("Huggies Size 4", Category.DIAPERS, "Size 4");
        Product otherStage1 = product("Similac Stage 1", Category.BABY_FOOD, "Stage 1");
        List<ListingAnalysis> all = List.of(
                plain(stage1, STORE_A, "9.00"),
                plain(otherStage1, STORE_B, "8.00"),
                plain(stage2, STORE_A, "7.00"),
                plain(size4, STORE_A, "1.00"));

        List<Insight> insights = InsightService.build(all, Set.of(stage1.getId()));

        assertThat(insights).singleElement().satisfies(i -> {
            assertThat(i.type()).isEqualTo("CHEAPEST_PER_UNIT");
            assertThat(i.message()).startsWith("Cheapest formula (Stage 1) is Similac");
            assertThat(i.productId()).isEqualTo(otherStage1.getId());
        });
    }

    @Test
    void anonymousUsersGetNoCheapestInsights() {
        Product huggies = product("Huggies Size 4", Category.DIAPERS, "Size 4");

        assertThat(InsightService.build(List.of(plain(huggies, STORE_A, "1.10")), Set.of())).isEmpty();
    }

    @Test
    void atMostFourInsightsWithWatchedDealsFirst() {
        List<ListingAnalysis> all = new ArrayList<>();
        Product watched = product("Huggies Size 3", Category.DIAPERS, "Size 3");
        all.add(listing(watched, STORE_A, "1.00", true, "16", false));
        for (int i = 0; i < 5; i++) {
            Product p = product("Brand" + i + " Size 5", Category.DIAPERS, "Size 5");
            all.add(listing(p, STORE_B, "1.50", true, String.valueOf(20 + i), false));
        }

        List<Insight> insights = InsightService.build(all, Set.of(watched.getId()));

        assertThat(insights).hasSize(InsightService.MAX_INSIGHTS);
        assertThat(insights.get(0).productId()).isEqualTo(watched.getId());
        // The rest are sorted by the size of the drop.
        assertThat(insights.get(1).message()).contains("24%");
    }

    @Test
    void manyDealsStillLeaveRoomForCheapest() {
        List<ListingAnalysis> all = new ArrayList<>();
        Product size3 = product("Huggies Size 3", Category.DIAPERS, "Size 3");
        Product stage1 = product("Materna Stage 1", Category.BABY_FOOD, "Stage 1");
        Product stage2 = product("Materna Stage 2", Category.BABY_FOOD, "Stage 2");
        all.add(plain(size3, STORE_A, "1.00"));
        all.add(plain(stage1, STORE_A, "9.00"));
        all.add(plain(stage2, STORE_A, "8.00"));
        for (int i = 0; i < 5; i++) {
            Product p = product("Brand" + i + " Size 5", Category.DIAPERS, "Size 5");
            all.add(listing(p, STORE_B, "1.50", true, String.valueOf(20 + i), false));
        }

        List<Insight> insights = InsightService.build(all, Set.of(size3.getId(), stage1.getId(), stage2.getId()));

        assertThat(insights).extracting(Insight::type)
                .containsExactly("GENUINE_DEAL", "GENUINE_DEAL", "CHEAPEST_PER_UNIT", "CHEAPEST_PER_UNIT");
    }
}
