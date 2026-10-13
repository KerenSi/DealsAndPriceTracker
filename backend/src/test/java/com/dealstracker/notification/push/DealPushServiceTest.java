package com.dealstracker.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.dealstracker.deals.DealEvaluation;
import com.dealstracker.deals.ListingAnalysis;
import com.dealstracker.domain.Category;
import com.dealstracker.domain.Product;
import com.dealstracker.domain.ProductInterest;
import com.dealstracker.domain.PushDealNotification;
import com.dealstracker.domain.Store;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DealPushServiceTest {

    private static final Store STORE_A = new Store("YOCHANANOF", "Yochananof Yad Eliyahu", "Tel Aviv");
    private static final Store STORE_B = new Store("OSHER_AD", "Osher Ad Yad Eliyahu", "Tel Aviv");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-13T06:00:00+03:00");

    private final Product huggies = product("Huggies Size 4");
    private final Product pampers = product("Pampers Size 4");

    private static Product product(String title) {
        Product p = new Product(UUID.randomUUID().toString(), title, Category.DIAPERS);
        ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
        return p;
    }

    private static ListingAnalysis deal(Product p, Store s, String price) {
        DealEvaluation eval = new DealEvaluation(true, new BigDecimal(price), new BigDecimal("60.00"), null,
                new BigDecimal("20"), null, false, List.of());
        return new ListingAnalysis(p, s, null, eval, null, "diaper");
    }

    private PushDealNotification pushed(Product p, String price, OffsetDateTime at) {
        return new PushDealNotification(new ProductInterest.Key(UUID.randomUUID(), p.getId()), new BigDecimal(price),
                at);
    }

    @Test
    void pushesOnlyStarredProductsWithTheirCheapestDeal() {
        List<ListingAnalysis> deals = List.of(deal(huggies, STORE_A, "52.00"), deal(huggies, STORE_B, "48.00"),
                deal(pampers, STORE_A, "45.00"));

        List<ListingAnalysis> push = DealPushService.dealsToPush(deals, Set.of(huggies.getId()), Map.of(), NOW);

        assertThat(push).singleElement().satisfies(a -> {
            assertThat(a.product()).isEqualTo(huggies);
            assertThat(a.store()).isEqualTo(STORE_B);
        });
    }

    @Test
    void anOngoingDealIsNotPushedAgain() {
        List<ListingAnalysis> deals = List.of(deal(huggies, STORE_A, "48.00"));

        assertThat(DealPushService.dealsToPush(deals, Set.of(huggies.getId()),
                Map.of(huggies.getId(), pushed(huggies, "48.00", NOW.minusDays(2))), NOW)).isEmpty();
    }

    @Test
    void aLowerPriceOrAWeekLaterIsPushedAgain() {
        List<ListingAnalysis> deals = List.of(deal(huggies, STORE_A, "45.00"), deal(pampers, STORE_A, "40.00"));

        List<ListingAnalysis> push = DealPushService.dealsToPush(deals, Set.of(huggies.getId(), pampers.getId()),
                Map.of(huggies.getId(), pushed(huggies, "48.00", NOW.minusDays(1)),
                        pampers.getId(), pushed(pampers, "40.00", NOW.minusDays(8))), NOW);

        assertThat(push).extracting(ListingAnalysis::product).containsExactlyInAnyOrder(huggies, pampers);
    }
}
