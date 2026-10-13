package com.dealstracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A user's mark on a product: watched or not interested. */
@Entity
@Table(name = "product_interests")
public class ProductInterest {

    @EmbeddedId
    private Key id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Interest interest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected ProductInterest() {
    }

    public ProductInterest(UUID userId, UUID productId, Interest interest) {
        this.id = new Key(userId, productId);
        this.interest = interest;
    }

    public Key getId() { return id; }
    public Interest getInterest() { return interest; }
    public void setInterest(Interest interest) { this.interest = interest; }

    @Embeddable
    public record Key(@Column(name = "user_id") UUID userId,
                      @Column(name = "product_id") UUID productId) implements Serializable {
    }
}
