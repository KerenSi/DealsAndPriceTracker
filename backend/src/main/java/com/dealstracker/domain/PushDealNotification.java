package com.dealstracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** The last deal a user was pushed for a product. */
@Entity
@Table(name = "push_deal_notifications")
public class PushDealNotification {

    @EmbeddedId
    private ProductInterest.Key id;

    @Column(name = "effective_price", nullable = false)
    private BigDecimal effectivePrice;

    @Column(name = "notified_at", nullable = false)
    private OffsetDateTime notifiedAt;

    protected PushDealNotification() {
    }

    public PushDealNotification(ProductInterest.Key id, BigDecimal effectivePrice, OffsetDateTime notifiedAt) {
        this.id = id;
        this.effectivePrice = effectivePrice;
        this.notifiedAt = notifiedAt;
    }

    public ProductInterest.Key getId() { return id; }
    public BigDecimal getEffectivePrice() { return effectivePrice; }
    public OffsetDateTime getNotifiedAt() { return notifiedAt; }

    public void update(BigDecimal effectivePrice, OffsetDateTime notifiedAt) {
        this.effectivePrice = effectivePrice;
        this.notifiedAt = notifiedAt;
    }
}
