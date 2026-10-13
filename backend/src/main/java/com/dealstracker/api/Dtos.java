package com.dealstracker.api;

import com.dealstracker.chat.ChatMessage;
import com.dealstracker.deals.DealEvaluation;
import com.dealstracker.domain.Category;
import com.dealstracker.domain.Interest;
import com.dealstracker.domain.NotificationChannel;
import com.dealstracker.domain.UnitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class Dtos {

    private Dtos() {
    }

    public record StoreDto(UUID id, String chain, String branchName, String city) {
    }

    public record ProductSummary(
            UUID id, String title, String brand, Category category, String sizeLabel,
            BigDecimal unitQuantity, UnitType unitType,
            BigDecimal lowestPrice, String lowestPriceStore, BigDecimal lowestUnitPrice, String unitLabel,
            int storeCount, boolean hasGenuineDeal, Interest interest) {
    }

    public record StorePrice(
            UUID listingId, StoreDto store, BigDecimal price, BigDecimal referencePrice, BigDecimal effectivePrice,
            BigDecimal unitPrice, String unitLabel, boolean promotion, String promotionDescription,
            OffsetDateTime scrapedAt, DealEvaluation deal) {
    }

    public record ProductDetail(ProductSummary product, List<StorePrice> prices) {
    }

    public record HistoryPoint(UUID listingId, String storeName, BigDecimal price, BigDecimal effectivePrice,
                               boolean promotion, OffsetDateTime scrapedAt) {
    }

    public record ChatRequest(@NotEmpty @Size(max = 40) List<@Valid ChatMessage> messages) {
    }

    public record ChatResponse(String reply) {
    }

    public record CreateAlertRequest(
            @NotNull UUID productId,
            @DecimalMin("1") @DecimalMax("90") BigDecimal targetDiscountPercentage,
            @NotNull NotificationChannel channel,
            @NotBlank String recipientId) {
    }

    public record AlertDto(UUID id, UUID productId, String productTitle, BigDecimal targetDiscountPercentage,
                           NotificationChannel channel, String recipientId, boolean active,
                           OffsetDateTime lastNotifiedAt) {
    }

    public record AuthRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record UserDto(UUID id, String email, boolean showSimilarProducts) {
    }

    public record SettingsRequest(boolean showSimilarProducts) {
    }

    public record InterestRequest(@NotNull Interest interest) {
    }

    public record AuthResponse(String token, OffsetDateTime expiresAt, UserDto user) {
    }
}
