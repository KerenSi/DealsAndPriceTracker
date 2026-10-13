package com.dealstracker.config;

import java.math.BigDecimal;
import java.util.List;
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
     * Israeli Price Transparency Law feeds. Each feed points at a PriceFull (and optionally PromoFull)
     * XML file - plain or gzipped - for one store branch.
     */
    public record Transparency(boolean enabled, List<Feed> feeds) {
    }

    public record Feed(String chain, String storeCode, String priceFileUrl, String promoFileUrl) {
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
