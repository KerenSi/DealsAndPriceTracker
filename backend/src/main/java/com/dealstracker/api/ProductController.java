package com.dealstracker.api;

import com.dealstracker.api.Dtos.HistoryPoint;
import com.dealstracker.api.Dtos.ProductDetail;
import com.dealstracker.api.Dtos.ProductSummary;
import com.dealstracker.api.Dtos.StoreDto;
import com.dealstracker.api.Dtos.StorePrice;
import com.dealstracker.auth.PreferencesService;
import com.dealstracker.auth.UserPreferences;
import com.dealstracker.deals.ListingAnalysis;
import com.dealstracker.deals.PriceAnalysisService;
import com.dealstracker.deals.PricePoint;
import com.dealstracker.domain.Category;
import com.dealstracker.domain.Interest;
import com.dealstracker.domain.Product;
import com.dealstracker.domain.Store;
import com.dealstracker.repository.ProductListingRepository;
import com.dealstracker.repository.ProductRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository products;
    private final ProductListingRepository listings;
    private final PriceAnalysisService analysis;
    private final PreferencesService preferences;

    public ProductController(ProductRepository products, ProductListingRepository listings,
                             PriceAnalysisService analysis, PreferencesService preferences) {
        this.products = products;
        this.listings = listings;
        this.analysis = analysis;
        this.preferences = preferences;
    }

    @GetMapping
    public List<ProductSummary> list(@RequestParam(required = false) Category category,
                                     @RequestParam(required = false) String q,
                                     @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                     String authorization) {
        UserPreferences prefs = preferences.forRequest(authorization);
        Map<UUID, List<ListingAnalysis>> byProduct = analysis.analyzeAll().stream()
                .collect(Collectors.groupingBy(a -> a.product().getId()));
        String query = q == null || q.isBlank() ? null : q.trim();
        return products.search(category, query).stream()
                .map(p -> summary(p, byProduct.getOrDefault(p.getId(), List.of()), prefs.interestIn(p.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public ProductDetail get(@PathVariable UUID id,
                             @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                             String authorization) {
        Product product = products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<ListingAnalysis> analyses = analysis.analyzeProduct(id);
        List<StorePrice> prices = analyses.stream()
                .map(a -> new StorePrice(a.latest().getListing().getId(), store(a.store()), a.latest().getPrice(),
                        a.latest().getOriginalReferencePrice(), a.evaluation().effectivePrice(), a.unitPrice(),
                        a.unitLabel(), a.latest().isPromotion(), a.latest().getPromotionDescription(),
                        a.latest().getScrapedAt(), a.evaluation()))
                .toList();
        Interest interest = preferences.forRequest(authorization).interestIn(id);
        return new ProductDetail(summary(product, analyses, interest), prices);
    }

    @GetMapping("/{id}/history")
    public List<HistoryPoint> history(@PathVariable UUID id, @RequestParam(defaultValue = "60") int days) {
        var productListings = listings.findByProductId(id);
        Map<UUID, String> storeNames = productListings.stream()
                .collect(Collectors.toMap(l -> l.getId(), l -> l.getStore().getBranchName()));
        return analysis.history(List.copyOf(storeNames.keySet()), Math.min(days, 365)).stream()
                .map(s -> new HistoryPoint(s.getListing().getId(), storeNames.get(s.getListing().getId()),
                        s.getPrice(), PricePoint.from(s).effectivePrice(), s.isPromotion(), s.getScrapedAt()))
                .toList();
    }

    private static ProductSummary summary(Product p, List<ListingAnalysis> analyses, Interest interest) {
        ListingAnalysis cheapest = analyses.stream()
                .min(Comparator.comparing((ListingAnalysis a) -> a.evaluation().effectivePrice()))
                .orElse(null);
        return new ProductSummary(p.getId(), p.getTitle(), p.getBrand(), p.getCategory(), p.getSizeLabel(),
                p.getUnitQuantity(), p.getUnitType(),
                cheapest == null ? null : cheapest.evaluation().effectivePrice(),
                cheapest == null ? null : cheapest.store().getBranchName(),
                cheapest == null ? null : cheapest.unitPrice(),
                cheapest == null ? null : cheapest.unitLabel(),
                analyses.size(),
                analyses.stream().anyMatch(a -> a.evaluation().genuineDeal()),
                interest);
    }

    static StoreDto store(Store s) {
        return new StoreDto(s.getId(), s.getChain(), s.getBranchName(), s.getCity());
    }
}
