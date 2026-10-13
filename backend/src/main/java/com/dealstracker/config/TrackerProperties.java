package com.dealstracker.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracker")
public record TrackerProperties(
        String timezone,
        List<String> corsAllowedOrigins,
        String adminToken,
        Ingestion ingestion,
        Deals deals,
        Sources sources,
        Notifications notifications,
        Chat chat) {

    public record Ingestion(String cron, boolean runOnStartup) {
    }

    public record Deals(BigDecimal defaultThresholdPercent, int baselineDays, int longBaselineDays) {
    }

    public record Sources(Sample sample, Transparency transparency) {
    }

    public record Sample(boolean enabled) {
    }

    /**
     * Israeli Price Transparency Law files. {@code portals} maps a store chain (e.g. TIV_TAAM) to the
     * site the chain publishes on; the latest PriceFull / PromoFull file for each branch is found there.
     * {@code feeds} pins fixed file URLs for one branch instead (useful for testing).
     */
    public record Transparency(boolean enabled, List<Feed> feeds, Map<String, Portal> portals) {
    }

    public record Feed(String chain, String storeCode, String priceFileUrl, String promoFileUrl) {
    }

    public enum PortalType { CERBERUS, BINA, WEB_INDEX }

    /**
     * @param url         CERBERUS / BINA: the site's base URL. WEB_INDEX: the listing URL template.
     * @param downloadUrl WEB_INDEX only: download URL template for files the listing has no link for.
     * @param maxPages    WEB_INDEX only: how many listing pages to read when the URL has {page}.
     */
    public record Portal(PortalType type, String url, String username, String password, String downloadUrl,
                         Integer maxPages) {
    }

    public record Notifications(Telegram telegram, WebPush webPush) {
    }

    /**
     * Browser push. Leave the keys empty to have the server generate and store a pair on first start;
     * subject is the contact (mailto: or https:) push services may use to reach whoever runs the server.
     */
    public record WebPush(String publicKey, String privateKey, String subject) {
    }

    public record Telegram(String botToken) {
    }

    public record Chat(String model, long maxTokens) {
    }
}
