package com.dealstracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "price_snapshots")
public class PriceSnapshot {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id")
    private ProductListing listing;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "original_reference_price")
    private BigDecimal originalReferencePrice;

    @Column(name = "is_promotion", nullable = false)
    private boolean promotion;

    @Column(name = "promotion_description")
    private String promotionDescription;

    @Column(name = "promo_min_quantity")
    private Integer promoMinQuantity;

    @Column(name = "promo_total_price")
    private BigDecimal promoTotalPrice;

    @Column(name = "scraped_at", nullable = false)
    private OffsetDateTime scrapedAt;

    protected PriceSnapshot() {
    }

    public PriceSnapshot(ProductListing listing, BigDecimal price, OffsetDateTime scrapedAt) {
        this.listing = listing;
        this.price = price;
        this.scrapedAt = scrapedAt;
    }

    public UUID getId() { return id; }
    public ProductListing getListing() { return listing; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getOriginalReferencePrice() { return originalReferencePrice; }
    public void setOriginalReferencePrice(BigDecimal v) { this.originalReferencePrice = v; }
    public boolean isPromotion() { return promotion; }
    public void setPromotion(boolean promotion) { this.promotion = promotion; }
    public String getPromotionDescription() { return promotionDescription; }
    public void setPromotionDescription(String v) { this.promotionDescription = v; }
    public Integer getPromoMinQuantity() { return promoMinQuantity; }
    public void setPromoMinQuantity(Integer v) { this.promoMinQuantity = v; }
    public BigDecimal getPromoTotalPrice() { return promoTotalPrice; }
    public void setPromoTotalPrice(BigDecimal v) { this.promoTotalPrice = v; }
    public OffsetDateTime getScrapedAt() { return scrapedAt; }
}
