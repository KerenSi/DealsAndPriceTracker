package com.dealstracker.insights;

import static org.assertj.core.api.Assertions.assertThat;

import com.dealstracker.auth.UserPreferences;
import com.dealstracker.deals.DealEvaluation;
import com.dealstracker.deals.ListingAnalysis;
import com.dealstracker.domain.Category;
import com.dealstracker.domain.Interest;
import com.dealstracker.domain.Product;
import com.dealstracker.domain.Store;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static ListingAnalysis deal(Product p, Store s, String drop) {
        return listing(p, s, "1.50", true, drop, false);
    }

    private static UserPreferences watching(Product... products) {
        return prefs(true, Map.of(), products);
    }

    private static UserPreferences prefs(boolean showSimilar, Map<UUID, Interest> extra, Product... watched) {
        Map<UUID, Interest> interests = new HashMap<>(extra);
        for (Product p : watched) {
            interests.put(p.getId(), Interest.WATCHED);
        }
        return new UserPreferences(interests, showSimilar);
    }

    @Test
    void suspiciousSalesAreNotShown() {
        Product huggies = product("Huggies Size 4", Category.DIAPERS, "Size 4");
        List<ListingAnalysis> all = List.of(listing(huggies, STORE_A, "1.20", false, "3", true));

        assertThat(InsightService.build(all, watching(huggies)))
                .extracting(Insight::type)
                .doesNotContain("FAKE_DISCOUNT");
    }

    @Test
    void realDealsComeFirstThenCheapest() {
        Product huggies = product("Huggies Size 4", Category.DIAPERS, "Size 4");
        Product pampers = product("Pampers Size 4", Category.DIAPERS, "Size 4");
        List<ListingAnalysis> all = List.of(plain(huggies, STORE_A, "1.10"), deal(pampers, STORE_B, "20"));

        List<Insight> insights = InsightService.build(all, watching(huggies));

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

        List<Insight> insights = InsightService.build(all, watching(stage1));

        assertThat(insights).singleElement().satisfies(i -> {
            assertThat(i.message()).startsWith("Cheapest formula (Stage 1) is Similac");
            assertThat(i.productId()).isEqualTo(otherStage1.getId());
        });
    }

    @Test
    void withoutSimilarProductsOnlyWatchedProductsCompete() {
        Product materna = product("Materna Stage 1", Category.BABY_FOOD, "Stage 1");
        Product similac = product("Similac Stage 1", Category.BABY_FOOD, "Stage 1");
        List<ListingAnalysis> all = List.of(
                plain(materna, STORE_A, "9.00"),
                plain(materna, STORE_B, "8.50"),
                plain(similac, STORE_B, "8.00"));

        List<Insight> insights = InsightService.build(all, prefs(false, Map.of(), materna));

        assertThat(insights).singleElement().satisfies(i -> {
            assertThat(i.productId()).isEqualTo(materna.getId());
            assertThat(i.storeName()).isEqualTo(STORE_B.getBranchName());
        });
    }

    @Test
    void notInterestedProductsAreLeftOutOfDealsAndCheapest() {
        Product huggies = product("Huggies Size 4", Category.DIAPERS, "Size 4");
        Product pampers = product("Pampers Size 4", Category.DIAPERS, "Size 4");
        List<ListingAnalysis> all = List.of(
                plain(huggies, STORE_A, "1.10"),
                plain(pampers, STORE_A, "0.90"),
                deal(pampers, STORE_B, "30"));

        List<Insight> insights = InsightService.build(all,
                prefs(true, Map.of(pampers.getId(), Interest.NOT_INTERESTED), huggies));

        assertThat(insights).singleElement().satisfies(i -> {
            assertThat(i.type()).isEqualTo("CHEAPEST_PER_UNIT");
            assertThat(i.productId()).isEqualTo(huggies.getId());
        });
    }

    @Test
    void anonymousUsersGetDealsOnly() {
        Product huggies = product("Huggies Size 4", Category.DIAPERS, "Size 4");
        Product pampers = product("Pampers Size 4", Category.DIAPERS, "Size 4");
        List<ListingAnalysis> all = List.of(plain(huggies, STORE_A, "1.10"), deal(pampers, STORE_B, "20"));

        assertThat(InsightService.build(all, UserPreferences.ANONYMOUS))
                .extracting(Insight::type).containsExactly("GENUINE_DEAL");
    }

    @Test
    void allDealsAreShownPlusTwoCheapest() {
        List<ListingAnalysis> all = new ArrayList<>();
        Product size3 = product("Huggies Size 3", Category.DIAPERS, "Size 3");
        Product stage1 = product("Materna Stage 1", Category.BABY_FOOD, "Stage 1");
        Product stage2 = product("Materna Stage 2", Category.BABY_FOOD, "Stage 2");
        all.add(plain(size3, STORE_A, "1.00"));
        all.add(plain(stage1, STORE_A, "9.00"));
        all.add(plain(stage2, STORE_A, "8.00"));
        for (int i = 0; i < 5; i++) {
            all.add(deal(product("Brand" + i + " Size 5", Category.DIAPERS, "Size 5"), STORE_B, "2" + i));
        }

        List<Insight> insights = InsightService.build(all, watching(size3, stage1, stage2));

        assertThat(insights).extracting(Insight::type).containsExactly(
                "GENUINE_DEAL", "GENUINE_DEAL", "GENUINE_DEAL", "GENUINE_DEAL", "GENUINE_DEAL",
                "CHEAPEST_PER_UNIT", "CHEAPEST_PER_UNIT");
        // Deals are sorted by the size of the drop.
        assertThat(insights.get(0).message()).contains("24%");
    }

    @Test
    void cheapestFillsUpToFourWhenThereAreFewDeals() {
        Product size3 = product("Huggies Size 3", Category.DIAPERS, "Size 3");
        Product size4 = product("Huggies Size 4", Category.DIAPERS, "Size 4");
        Product stage1 = product("Materna Stage 1", Category.BABY_FOOD, "Stage 1");
        Product stage2 = product("Materna Stage 2", Category.BABY_FOOD, "Stage 2");
        List<ListingAnalysis> all = List.of(
                plain(size3, STORE_A, "1.00"), plain(size4, STORE_A, "1.10"),
                plain(stage1, STORE_A, "9.00"), plain(stage2, STORE_A, "8.00"),
                deal(product("Pampers Size 5", Category.DIAPERS, "Size 5"), STORE_B, "20"));

        List<Insight> insights = InsightService.build(all, watching(size3, size4, stage1, stage2));

        assertThat(insights).extracting(Insight::type).containsExactly(
                "GENUINE_DEAL", "CHEAPEST_PER_UNIT", "CHEAPEST_PER_UNIT", "CHEAPEST_PER_UNIT");
    }

    @Test
    void dealsOnWatchedProductsComeFirst() {
        Product watched = product("Huggies Size 3", Category.DIAPERS, "Size 3");
        Product other = product("Pampers Size 5", Category.DIAPERS, "Size 5");
        List<ListingAnalysis> all = List.of(deal(other, STORE_B, "40"), deal(watched, STORE_A, "16"));

        assertThat(InsightService.build(all, watching(watched)).get(0).productId()).isEqualTo(watched.getId());
    }
}
