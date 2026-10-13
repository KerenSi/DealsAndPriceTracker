package com.dealstracker.ingestion;

import com.dealstracker.domain.PriceSnapshot;
import com.dealstracker.domain.Product;
import com.dealstracker.domain.ProductListing;
import com.dealstracker.domain.Store;
import com.dealstracker.repository.PriceSnapshotRepository;
import com.dealstracker.repository.ProductListingRepository;
import com.dealstracker.repository.ProductRepository;
import com.dealstracker.repository.StoreRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    public record Result(int stores, int snapshots, int failures) {
    }

    private final List<PriceSource> sources;
    private final StoreRepository stores;
    private final ProductRepository products;
    private final ProductListingRepository listings;
    private final PriceSnapshotRepository snapshots;
    private final TransactionTemplate tx;
    private final Clock clock;

    public IngestionService(List<PriceSource> sources, StoreRepository stores, ProductRepository products,
                            ProductListingRepository listings, PriceSnapshotRepository snapshots,
                            TransactionTemplate tx, Clock clock) {
        this.sources = sources;
        this.stores = stores;
        this.products = products;
        this.listings = listings;
        this.snapshots = snapshots;
        this.tx = tx;
        this.clock = clock;
    }

    public Result ingestAll() {
        return ingest(LocalDate.now(clock), OffsetDateTime.now(clock));
    }

    /** Ingests prices for every enabled store. Each store is committed separately. */
    public Result ingest(LocalDate date, OffsetDateTime observedAt) {
        int storeCount = 0;
        int snapshotCount = 0;
        int failures = 0;
        for (Store store : stores.findByEnabledTrueOrderByChain()) {
            for (PriceSource source : sources) {
                if (!source.supports(store)) {
                    continue;
                }
                try {
                    List<ScrapedPrice> prices = source.fetch(store, date);
                    Integer saved = tx.execute(status -> save(store, prices, observedAt));
                    snapshotCount += saved == null ? 0 : saved;
                    storeCount++;
                    log.info("Ingested {} prices for {} from {}", prices.size(), store.getBranchName(), source.name());
                } catch (Exception e) {
                    failures++;
                    log.error("Ingestion failed for {} from {}", store.getBranchName(), source.name(), e);
                }
            }
        }
        return new Result(storeCount, snapshotCount, failures);
    }

    private int save(Store store, List<ScrapedPrice> prices, OffsetDateTime observedAt) {
        int count = 0;
        for (ScrapedPrice sp : prices) {
            Product product = products.findByBarcode(sp.barcode())
                    .orElseGet(() -> new Product(sp.barcode(), sp.title(), sp.category()));
            product.setTitle(sp.title());
            if (sp.brand() != null) product.setBrand(sp.brand());
            if (sp.sizeLabel() != null) product.setSizeLabel(sp.sizeLabel());
            if (sp.unitQuantity() != null) product.setUnitQuantity(sp.unitQuantity());
            if (sp.unitType() != null) product.setUnitType(sp.unitType());
            product = products.save(product);

            Product p = product;
            ProductListing listing = listings.findByProductIdAndStoreId(p.getId(), store.getId())
                    .orElseGet(() -> listings.save(new ProductListing(p, store)));
            if (sp.productUrl() != null) {
                listing.setProductUrl(sp.productUrl());
            }

            PriceSnapshot snap = new PriceSnapshot(listing, sp.price(), observedAt);
            snap.setOriginalReferencePrice(sp.referencePrice());
            snap.setPromotion(sp.promotion());
            snap.setPromotionDescription(sp.promotionDescription());
            snap.setPromoMinQuantity(sp.promoMinQuantity());
            snap.setPromoTotalPrice(sp.promoTotalPrice());
            snapshots.save(snap);
            count++;
        }
        return count;
    }
}
