package com.dealstracker.ingestion;

import com.dealstracker.config.TrackerProperties;
import com.dealstracker.domain.Store;
import com.dealstracker.ingestion.portal.BinaProjectsPortal;
import com.dealstracker.ingestion.portal.CerberusPortal;
import com.dealstracker.ingestion.portal.ChainPortal;
import com.dealstracker.ingestion.portal.PortalHttp;
import com.dealstracker.ingestion.portal.TransparencyFileName;
import com.dealstracker.ingestion.portal.WebIndexPortal;
import com.dealstracker.repository.StoreRepository;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reads prices from the files chains publish under the Israeli Price Transparency Law.
 *
 * <p>For a chain with a portal under {@code tracker.sources.transparency.portals}, the newest
 * PriceFull and PromoFull files for the branch are found by listing the portal. A branch without a
 * store_code gets one by matching its store_match words against the chain's Stores file; the code
 * is saved. A portal login only sees its own chain's files, so files are not filtered by ChainId
 * (the configured chain_code is corrected from the Stores file). Fixed per-branch URLs under {@code feeds} take precedence.
 */
@Component
@ConditionalOnProperty(prefix = "tracker.sources.transparency", name = "enabled", havingValue = "true")
public class TransparencyPriceSource implements PriceSource {

    private static final Logger log = LoggerFactory.getLogger(TransparencyPriceSource.class);

    private final List<TrackerProperties.Feed> feeds;
    private final Map<String, TrackerProperties.Portal> portals;
    private final StoreRepository stores;
    private final TransparencyFileParser parser = new TransparencyFileParser();

    public TransparencyPriceSource(TrackerProperties props, StoreRepository stores) {
        TrackerProperties.Transparency config = props.sources().transparency();
        this.feeds = Objects.requireNonNullElse(config.feeds(), List.of());
        this.portals = Objects.requireNonNullElse(config.portals(), Map.of());
        this.stores = stores;
    }

    @Override
    public String name() {
        return "transparency";
    }

    @Override
    public boolean supports(Store store) {
        return feedFor(store).isPresent() || portalConfig(store).isPresent();
    }

    @Override
    public List<ScrapedPrice> fetch(Store store, LocalDate date) throws Exception {
        Optional<TrackerProperties.Feed> feed = feedFor(store);
        if (feed.isPresent()) {
            return fetchFeed(feed.get());
        }
        ChainPortal portal = createPortal(portalConfig(store).orElseThrow());
        String storeCode = store.getStoreCode() != null ? store.getStoreCode() : resolveStoreCode(portal, store, date);

        TransparencyFileName priceFile = TransparencyFileName.latest(
                        portal.list(TransparencyFileName.PRICE_FULL, storeCode, date),
                        TransparencyFileName.PRICE_FULL, null, storeCode)
                .orElseThrow(() -> new IllegalStateException(
                        "No PriceFull file for " + store.getBranchName() + " (store " + storeCode + ")"));
        Optional<TransparencyFileName> promoFile = TransparencyFileName.latest(
                portal.list(TransparencyFileName.PROMO_FULL, storeCode, date),
                TransparencyFileName.PROMO_FULL, null, storeCode);

        Map<String, TransparencyFileParser.Promotion> promos = Map.of();
        if (promoFile.isPresent()) {
            try (InputStream in = portal.download(promoFile.get())) {
                promos = parser.parsePromotions(in);
            }
        } else {
            log.warn("No PromoFull file for {}; prices only", store.getBranchName());
        }
        log.info("{}: reading {}", store.getBranchName(), priceFile.name());
        try (InputStream in = portal.download(priceFile)) {
            return parser.parsePrices(in, promos);
        }
    }

    /** Finds the branch in the chain's Stores file by its store_match words and saves the code. */
    String resolveStoreCode(ChainPortal portal, Store store, LocalDate date) throws Exception {
        if (store.getStoreMatch() == null || store.getStoreMatch().isBlank()) {
            throw new IllegalStateException(store.getBranchName() + " has neither store_code nor store_match");
        }
        TransparencyFileName storesFile = TransparencyFileName.latest(
                        portal.list(TransparencyFileName.STORES, null, date),
                        TransparencyFileName.STORES, null, null)
                .orElseThrow(() -> new IllegalStateException("No Stores file for chain " + store.getChain()));
        List<TransparencyFileParser.StoreInfo> branches;
        try (InputStream in = portal.download(storesFile)) {
            branches = parser.parseStores(in);
        }
        // Two tracked branches of the same chain can share the match words (Super-Pharm Yigal Alon),
        // so skip codes already taken by another row.
        Set<String> taken = stores.findByChainAndEnabledTrue(store.getChain()).stream()
                .map(Store::getStoreCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<TransparencyFileParser.StoreInfo> matches = branches.stream()
                .filter(b -> b.matches(store.getStoreMatch()))
                .toList();
        TransparencyFileParser.StoreInfo branch = matches.stream()
                .filter(b -> taken.stream().noneMatch(t -> TransparencyFileName.sameCode(t, b.storeCode())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No branch matching '" + store.getStoreMatch()
                        + "' in " + storesFile.name() + " (matches: " + matches + ")"));
        if (matches.size() > 1) {
            log.warn("Several branches match '{}' for {}: {}; using {}", store.getStoreMatch(),
                    store.getBranchName(), matches, branch);
        }
        log.info("Resolved {} to store {} ({}, {})", store.getBranchName(), branch.storeCode(), branch.name(),
                branch.address());
        store.setCodes(storesFile.chainCode(), branch.storeCode());
        stores.save(store);
        return branch.storeCode();
    }

    private List<ScrapedPrice> fetchFeed(TrackerProperties.Feed feed) throws Exception {
        PortalHttp http = new PortalHttp();
        Map<String, TransparencyFileParser.Promotion> promos = Map.of();
        if (feed.promoFileUrl() != null && !feed.promoFileUrl().isBlank()) {
            try (InputStream in = http.download(feed.promoFileUrl())) {
                promos = parser.parsePromotions(in);
            }
        }
        try (InputStream in = http.download(feed.priceFileUrl())) {
            return parser.parsePrices(in, promos);
        }
    }

    static ChainPortal createPortal(TrackerProperties.Portal p) {
        return switch (p.type()) {
            case CERBERUS -> new CerberusPortal(p.url(), p.username(), p.password());
            case BINA -> new BinaProjectsPortal(p.url());
            case WEB_INDEX -> new WebIndexPortal(p.url(), p.downloadUrl(), p.maxPages());
        };
    }

    private Optional<TrackerProperties.Portal> portalConfig(Store store) {
        return portals.entrySet().stream()
                .filter(e -> chainKey(e.getKey()).equals(chainKey(store.getChain())))
                .map(Map.Entry::getValue)
                .filter(p -> p != null && p.type() != null && p.url() != null && !p.url().isBlank())
                .findFirst();
    }

    /** Spring may drop the "_" from map keys like TIV_TAAM when binding, so compare without separators. */
    private static String chainKey(String chain) {
        return chain.replaceAll("[^A-Za-z0-9]", "").toUpperCase(java.util.Locale.ROOT);
    }

    private Optional<TrackerProperties.Feed> feedFor(Store store) {
        return feeds.stream()
                .filter(f -> f.chain().equalsIgnoreCase(store.getChain())
                        && Objects.equals(f.storeCode(), store.getStoreCode()))
                .findFirst();
    }
}
