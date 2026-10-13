package com.dealstracker.ingestion;

import com.dealstracker.config.TrackerProperties;
import com.dealstracker.domain.Store;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reads prices from Price Transparency Law files configured per store under
 * {@code tracker.sources.transparency.feeds}.
 *
 * <p>TODO: each chain publishes through a different portal (e.g. publishedprices.co.il for several
 * chains, dedicated sites for others) and file names change daily. Add per-chain discovery of the
 * latest PriceFull/PromoFull file so feeds don't need fixed URLs.
 */
@Component
@ConditionalOnProperty(prefix = "tracker.sources.transparency", name = "enabled", havingValue = "true")
public class TransparencyPriceSource implements PriceSource {

    private final List<TrackerProperties.Feed> feeds;
    private final TransparencyFileParser parser = new TransparencyFileParser();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public TransparencyPriceSource(TrackerProperties props) {
        this.feeds = Objects.requireNonNullElse(props.sources().transparency().feeds(), List.of());
    }

    @Override
    public String name() {
        return "transparency";
    }

    @Override
    public boolean supports(Store store) {
        return feedFor(store).isPresent();
    }

    @Override
    public List<ScrapedPrice> fetch(Store store, LocalDate date) throws Exception {
        TrackerProperties.Feed feed = feedFor(store).orElseThrow();
        Map<String, TransparencyFileParser.Promotion> promos = Map.of();
        if (feed.promoFileUrl() != null && !feed.promoFileUrl().isBlank()) {
            try (InputStream in = download(feed.promoFileUrl())) {
                promos = parser.parsePromotions(in);
            }
        }
        try (InputStream in = download(feed.priceFileUrl())) {
            return parser.parsePrices(in, promos);
        }
    }

    private Optional<TrackerProperties.Feed> feedFor(Store store) {
        return feeds.stream()
                .filter(f -> f.chain().equalsIgnoreCase(store.getChain())
                        && Objects.equals(f.storeCode(), store.getStoreCode()))
                .findFirst();
    }

    private InputStream download(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build();
        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() / 100 != 2) {
            resp.body().close();
            throw new IllegalStateException("GET " + url + " returned " + resp.statusCode());
        }
        return resp.body();
    }
}
